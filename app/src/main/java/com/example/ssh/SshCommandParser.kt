package com.example.ssh

data class PortForwardRule(
    val localPort: Int,
    val remoteHost: String = "127.0.0.1",
    val remotePort: Int
)

data class SshCommandAugmentation(
    val originalCommand: String,
    val hasExistingL: Boolean,
    val existingForwardings: List<PortForwardRule>,
    val effectiveCommand: String,
    val allForwardings: List<PortForwardRule>,
    val browserProxyPort: Int
)

data class ParsedSshCommand(
    val host: String = "",
    val port: Int? = null,
    val hasExplicitPort: Boolean = false,
    val hasPortPlaceholder: Boolean = false,
    val username: String = "root",
    val keyIdentityHint: String = "",
    val detectedLocalPort: Int? = null,
    val detectedRemotePort: Int? = null,
    val originalInput: String = ""
)

object SshCommandParser {

    /**
     * Sanitizes input string by stripping enclosing quotes and converting all
     * non-breaking/exotic web spaces into standard spaces.
     */
    fun cleanInput(input: String): String {
        var s = input.trim()
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")) ||
            (s.startsWith("`") && s.endsWith("`")) || (s.startsWith("“") && s.endsWith("”")) ||
            (s.startsWith("‘") && s.endsWith("’"))
        ) {
            s = s.substring(1, s.length - 1).trim()
        }
        return s.replace('\u00A0', ' ')
            .replace('\u2007', ' ')
            .replace('\u202F', ' ')
            .replace('\u200B', ' ')
            .replace('\uFEFF', ' ')
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\t", " ")
            .trim()
    }

    /**
     * Parses standard and cloud-specific SSH commands and connection strings such as:
     * - "ssh -p 40946 root@95.169.14.15 -L 8080:localhost:8080" (Vast.ai format)
     * - "ssh -p port root@x.x.x.x -L 8080:localhost:8080" (Vast.ai template format)
     * - "ssh root@194.26.196.12 -p 22022 -i ~/.ssh/id_ed25519" (RunPod)
     * - "ssh -p22022 root@194.26.196.12"
     * - "ssh -P 22022 root@194.26.196.12"
     * - "ssh -o Port=22022 root@194.26.196.12"
     * - "ssh://root@194.26.196.12:22022"
     */
    fun parse(input: String): ParsedSshCommand {
        val clean = cleanInput(input)
        if (clean.isEmpty()) return ParsedSshCommand()

        // 1. URI format check: ssh://user@host:port
        if (clean.startsWith("ssh://", ignoreCase = true)) {
            return parseUri(clean)
        }

        // 2. Key-Value / Labelled dashboard text: "Host: ... Port: ..."
        if (clean.contains("Port:", ignoreCase = true) || clean.contains("IP:", ignoreCase = true)) {
            val labelled = parseLabelledText(clean)
            if (labelled != null) return labelled
        }

        // 3. Extract all port forwarding specifications (-L)
        val forwardRules = extractAllForwardRules(clean)
        val detectedLocalPort = forwardRules.firstOrNull()?.localPort
        val detectedRemotePort = forwardRules.firstOrNull()?.remotePort

        // 4. Tokenize command line preserving quotes
        val tokens = tokenize(clean)
        var host = ""
        var explicitPort: Int? = null
        var hasPortPlaceholder = false
        var username = "root"
        var keyHint = ""

        var i = 0
        while (i < tokens.size) {
            val rawToken = tokens[i]
            val token = rawToken.trim('\'', '"')

            when {
                token.equals("ssh", ignoreCase = true) -> {
                    // Skip leading ssh binary name
                }

                // Flag: -p <port> or -P <port>
                token.equals("-p", ignoreCase = true) || token == "-P" -> {
                    if (i + 1 < tokens.size) {
                        val nextToken = tokens[i + 1].trim('\'', '"', '=')
                        val parsedNum = nextToken.toIntOrNull()
                        if (parsedNum != null && isValidPort(parsedNum)) {
                            explicitPort = parsedNum
                        } else if (isPortPlaceholder(nextToken)) {
                            hasPortPlaceholder = true
                        }
                        i++
                    }
                }

                // Flag: -p12345 or -P12345 or -p=12345 (avoid matching flags like -passphrase)
                (token.startsWith("-p", ignoreCase = true) || token.startsWith("-P")) &&
                        token.length > 2 && token[2].isDigit() -> {
                    val candidate = token.substring(2).trim('\'', '"', '=')
                    val parsedNum = candidate.toIntOrNull()
                    if (parsedNum != null && isValidPort(parsedNum)) {
                        explicitPort = parsedNum
                    } else if (isPortPlaceholder(candidate)) {
                        hasPortPlaceholder = true
                    }
                }

                // Flag: --port <port> or --port=<port>
                token.equals("--port", ignoreCase = true) -> {
                    if (i + 1 < tokens.size) {
                        val nextToken = tokens[i + 1].trim('\'', '"', '=')
                        val parsedNum = nextToken.toIntOrNull()
                        if (parsedNum != null && isValidPort(parsedNum)) {
                            explicitPort = parsedNum
                        } else if (isPortPlaceholder(nextToken)) {
                            hasPortPlaceholder = true
                        }
                        i++
                    }
                }
                token.startsWith("--port=", ignoreCase = true) -> {
                    val candidate = token.substring("--port=".length).trim('\'', '"')
                    val parsedNum = candidate.toIntOrNull()
                    if (parsedNum != null && isValidPort(parsedNum)) {
                        explicitPort = parsedNum
                    } else if (isPortPlaceholder(candidate)) {
                        hasPortPlaceholder = true
                    }
                }

                // Flag: -o Port=12345 or -o "Port 12345"
                token.equals("-o", ignoreCase = true) -> {
                    if (i + 1 < tokens.size) {
                        val opt = tokens[i + 1].trim('\'', '"')
                        val portMatch = Regex("""(?i)^Port\s*=?\s*([a-zA-Z0-9_-]+)$""").find(opt)
                        if (portMatch != null) {
                            val candidate = portMatch.groupValues[1]
                            val parsedNum = candidate.toIntOrNull()
                            if (parsedNum != null && isValidPort(parsedNum)) {
                                explicitPort = parsedNum
                            } else if (isPortPlaceholder(candidate)) {
                                hasPortPlaceholder = true
                            }
                        }
                        i++
                    }
                }
                token.startsWith("-oPort=", ignoreCase = true) -> {
                    val candidate = token.substring("-oPort=".length).trim('\'', '"')
                    val parsedNum = candidate.toIntOrNull()
                    if (parsedNum != null && isValidPort(parsedNum)) {
                        explicitPort = parsedNum
                    } else if (isPortPlaceholder(candidate)) {
                        hasPortPlaceholder = true
                    }
                }

                // Flag: -i <identity_file>
                token == "-i" -> {
                    if (i + 1 < tokens.size) {
                        keyHint = tokens[i + 1].trim('\'', '"')
                        i++
                    }
                }
                token.startsWith("-i") && token.length > 2 -> {
                    keyHint = token.substring(2).trim('\'', '"', '=')
                }

                // Flag: -l <username> (OpenSSH login name)
                token == "-l" -> {
                    if (i + 1 < tokens.size) {
                        username = tokens[i + 1].trim('\'', '"')
                        i++
                    }
                }
                token.startsWith("-l") && token.length > 2 -> {
                    username = token.substring(2).trim('\'', '"', '=')
                }

                // Flag: -L port forwarding (skip spec token in loop)
                token.equals("-L", ignoreCase = false) -> {
                    if (i + 1 < tokens.size) {
                        i++
                    }
                }

                token.startsWith("-") -> {
                    // Skip other boolean/string flags like -N, -v, -C, -T
                }

                else -> {
                    // Positional token: could be [user@]host[:port] or trailing port number
                    if (host.isEmpty()) {
                        val parsedTarget = parseTarget(token)
                        if (parsedTarget.first.isNotBlank() && !parsedTarget.first.contains(":")) {
                            host = parsedTarget.first
                            parsedTarget.second?.let { username = it }
                            parsedTarget.third?.let {
                                if (isValidPort(it)) explicitPort = it
                            }
                        }
                    } else if (explicitPort == null) {
                        // If host is already set, a trailing pure integer token is often a port
                        token.toIntOrNull()?.let {
                            if (isValidPort(it)) explicitPort = it
                        }
                    }
                }
            }
            i++
        }

        // 5. Fallback regex search for explicit port flag if token parsing missed it
        if (explicitPort == null) {
            val portFlagRegex = Regex("""(?i)(?:^|\s)(?:-p|-P|--port)\s*=?\s*['"]?(\d{2,5})['"]?""")
            portFlagRegex.find(clean)?.let { match ->
                match.groupValues[1].toIntOrNull()?.let {
                    if (isValidPort(it)) explicitPort = it
                }
            }
        }

        // 6. Fallback regex search for user@host if positional token missed it
        if (host.isEmpty()) {
            val userHostRegex = Regex("""(?i)(?:^|\s)([a-zA-Z0-9_.-]+)@([a-zA-Z0-9_.-]+)""")
            userHostRegex.find(clean)?.let { match ->
                username = match.groupValues[1]
                host = match.groupValues[2]
            }
        }

        // 7. Check for literal placeholder in raw text like "-p port" or "-p <port>"
        if (explicitPort == null && !hasPortPlaceholder) {
            val lower = clean.lowercase()
            if (lower.contains("-p port") || lower.contains("-p <port>") ||
                lower.contains("-p=port") || lower.contains("-p=<port>")
            ) {
                hasPortPlaceholder = true
            }
        }

        // 8. Fallback regex search for host:port if host was found but port was trailing
        if (explicitPort == null && host.isNotEmpty()) {
            val hostPortRegex = Regex("""${Regex.escape(host)}:(\d{2,5})""")
            hostPortRegex.find(clean)?.let { match ->
                match.groupValues[1].toIntOrNull()?.let {
                    if (isValidPort(it)) explicitPort = it
                }
            }
        }

        val hasExplicit = explicitPort != null

        return ParsedSshCommand(
            host = host,
            port = explicitPort,
            hasExplicitPort = hasExplicit,
            hasPortPlaceholder = hasPortPlaceholder,
            username = username,
            keyIdentityHint = keyHint,
            detectedLocalPort = detectedLocalPort,
            detectedRemotePort = detectedRemotePort,
            originalInput = clean
        )
    }

    private fun isPortPlaceholder(str: String): Boolean {
        val s = str.trim('<', '>', '$', '{', '}').lowercase()
        return s == "port" || s == "ssh_port" || s == "your_port" || s == "x"
    }

    private fun parseUri(uriStr: String): ParsedSshCommand {
        val clean = uriStr.substring("ssh://".length)
        var username = "root"
        var host = clean
        var port: Int? = null
        var hasExplicitPort = false

        if (clean.contains("@")) {
            val userSplit = clean.split("@", limit = 2)
            username = userSplit[0]
            host = userSplit[1]
        }

        if (host.contains(":")) {
            val hostPort = host.split(":", limit = 2)
            host = hostPort[0]
            hostPort[1].substringBefore("/").toIntOrNull()?.let {
                if (isValidPort(it)) {
                    port = it
                    hasExplicitPort = true
                }
            }
        }

        return ParsedSshCommand(
            host = host.trimEnd('/'),
            port = port,
            hasExplicitPort = hasExplicitPort,
            username = username,
            originalInput = uriStr
        )
    }

    private fun parseLabelledText(text: String): ParsedSshCommand? {
        val hostRegex = Regex("""(?i)(?:Host|IP|Server)\s*[:=]?\s*([a-zA-Z0-9_.-]+)""")
        val portRegex = Regex("""(?i)Port\s*[:=]?\s*(\d{2,5})""")
        val userRegex = Regex("""(?i)(?:User|Username)\s*[:=]?\s*([a-zA-Z0-9_.-]+)""")

        val hostMatch = hostRegex.find(text)
        val portMatch = portRegex.find(text)
        val userMatch = userRegex.find(text)

        if (hostMatch != null) {
            val host = hostMatch.groupValues[1]
            val port = portMatch?.groupValues?.get(1)?.toIntOrNull()
            val hasExplicit = port != null
            val user = userMatch?.groupValues?.get(1) ?: "root"

            return ParsedSshCommand(
                host = host,
                port = port,
                hasExplicitPort = hasExplicit,
                username = user,
                originalInput = text
            )
        }
        return null
    }

    private fun parseTarget(target: String): Triple<String, String?, Int?> {
        var cleanTarget = target.trim('\'', '"')
        var user: String? = null
        var port: Int? = null

        if (cleanTarget.contains("@")) {
            val parts = cleanTarget.split("@", limit = 2)
            user = parts[0]
            cleanTarget = parts[1]
        }

        if (cleanTarget.contains(":") && !cleanTarget.contains("[")) {
            val parts = cleanTarget.split(":", limit = 2)
            cleanTarget = parts[0]
            parts[1].substringBefore("/").toIntOrNull()?.let {
                if (isValidPort(it)) port = it
            }
        }

        return Triple(cleanTarget, user, port)
    }

    private fun tokenize(command: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false

        for (c in command) {
            when {
                c == '\'' && !inDoubleQuote -> inSingleQuote = !inSingleQuote
                c == '"' && !inSingleQuote -> inDoubleQuote = !inDoubleQuote
                (c.isWhitespace() || c == ' ' || c == '\u00A0' || c == '\t') && !inSingleQuote && !inDoubleQuote -> {
                    if (sb.isNotEmpty()) {
                        tokens.add(sb.toString())
                        sb.clear()
                    }
                }
                else -> sb.append(c)
            }
        }
        if (sb.isNotEmpty()) {
            tokens.add(sb.toString())
        }
        return tokens
    }

    fun isValidPort(port: Int): Boolean = port in 1..65535

    /**
     * Extracts all -L port forwardings specified in the command string.
     * Supports `-L local:remoteHost:remotePort` and `-L local:remotePort`.
     */
    fun extractAllForwardRules(command: String): List<PortForwardRule> {
        val clean = cleanInput(command)
        val rules = mutableListOf<PortForwardRule>()

        // Format 1: -L [localPort]:[remoteHost]:[remotePort]
        val regex3Parts = Regex("""(?i)-L\s*['"]?(\d+):([a-zA-Z0-9_.-]+):(\d+)['"]?""")
        for (match in regex3Parts.findAll(clean)) {
            val local = match.groupValues[1].toIntOrNull()
            val host = match.groupValues[2]
            val remote = match.groupValues[3].toIntOrNull()
            if (local != null && remote != null) {
                rules.add(
                    PortForwardRule(
                        localPort = local,
                        remoteHost = if (host.equals("localhost", ignoreCase = true)) "127.0.0.1" else host,
                        remotePort = remote
                    )
                )
            }
        }

        // Format 2: -L [localPort]:[remotePort] (defaulting to localhost / 127.0.0.1)
        val regex2Parts = Regex("""(?i)-L\s*['"]?(\d+):(\d+)['"]?""")
        for (match in regex2Parts.findAll(clean)) {
            val local = match.groupValues[1].toIntOrNull()
            val remote = match.groupValues[2].toIntOrNull()
            if (local != null && remote != null) {
                if (rules.none { it.localPort == local && it.remotePort == remote }) {
                    rules.add(PortForwardRule(localPort = local, remoteHost = "127.0.0.1", remotePort = remote))
                }
            }
        }

        // Format 3: Multiple specs listed after -L e.g. -L 8080:localhost:8080 7860:localhost:7860
        val regexMulti = Regex("""(?i)(?:localhost|127\.0\.0\.1|[a-zA-Z0-9_.-]+):\d+['"]?\s+['"]?(\d+):([a-zA-Z0-9_.-]+):(\d+)['"]?""")
        for (match in regexMulti.findAll(clean)) {
            val local = match.groupValues[1].toIntOrNull()
            val host = match.groupValues[2]
            val remote = match.groupValues[3].toIntOrNull()
            if (local != null && remote != null) {
                if (rules.none { it.localPort == local && it.remotePort == remote }) {
                    rules.add(
                        PortForwardRule(
                            localPort = local,
                            remoteHost = if (host.equals("localhost", ignoreCase = true)) "127.0.0.1" else host,
                            remotePort = remote
                        )
                    )
                }
            }
        }

        return rules
    }

    /**
     * Inspects the input SSH command for -L (port mirroring).
     * If -L is present, adds our desired browser proxy port alongside the existing -L.
     * If no -L is present, creates one (-L <proxyPort>:localhost:<proxyPort>).
     * Other than adding the browser proxy port mirror, the SSH command is NOT modified!
     */
    fun augmentCommandWithProxyPort(command: String, proxyPort: Int): SshCommandAugmentation {
        val clean = cleanInput(command)
        if (clean.isBlank()) {
            val eff = if (proxyPort in 1..65535) "-L $proxyPort:localhost:$proxyPort" else ""
            val fwd = if (proxyPort in 1..65535) listOf(PortForwardRule(proxyPort, "127.0.0.1", proxyPort)) else emptyList()
            return SshCommandAugmentation(
                originalCommand = command,
                hasExistingL = false,
                existingForwardings = emptyList(),
                effectiveCommand = eff,
                allForwardings = fwd,
                browserProxyPort = proxyPort
            )
        }

        val existingRules = extractAllForwardRules(clean)
        val hasExistingL = existingRules.isNotEmpty() || Regex("""(?i)-L\b""").containsMatchIn(clean)
        val alreadyHasProxyPort = existingRules.any { it.remotePort == proxyPort }

        // Literally slap -L on the end of whatever they give (unless already mirroring that port)
        val effectiveCommand = when {
            alreadyHasProxyPort -> clean
            else -> "$clean -L $proxyPort:localhost:$proxyPort"
        }

        val allForwardings = mutableListOf<PortForwardRule>()
        allForwardings.addAll(existingRules)
        if (!alreadyHasProxyPort && proxyPort in 1..65535) {
            allForwardings.add(PortForwardRule(localPort = proxyPort, remoteHost = "127.0.0.1", remotePort = proxyPort))
        }

        return SshCommandAugmentation(
            originalCommand = command,
            hasExistingL = hasExistingL,
            existingForwardings = existingRules,
            effectiveCommand = effectiveCommand,
            allForwardings = allForwardings,
            browserProxyPort = proxyPort
        )
    }
}
