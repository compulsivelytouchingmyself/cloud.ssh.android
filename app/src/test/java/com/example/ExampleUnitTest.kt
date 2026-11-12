package com.example

import com.example.ssh.SshCommandParser
import com.example.ssh.SshKeyHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testParseStandardSshCommand() {
        val input = "ssh root@194.26.196.12 -p 12345 -i ~/.ssh/id_ed25519"
        val parsed = SshCommandParser.parse(input)

        assertEquals("194.26.196.12", parsed.host)
        assertEquals(12345, parsed.port)
        assertTrue(parsed.hasExplicitPort)
        assertEquals("root", parsed.username)
        assertEquals("~/.ssh/id_ed25519", parsed.keyIdentityHint)
    }

    @Test
    fun testParseExactVastFormatWithNumericPort() {
        // Vast.ai format: ssh -p port root@x.x.x.x -L 8080:localhost:8080
        val input = "ssh -p 34567 root@192.0.2.1 -L 8080:localhost:8080"
        val parsed = SshCommandParser.parse(input)

        assertEquals("192.0.2.1", parsed.host)
        assertEquals(34567, parsed.port)
        assertTrue(parsed.hasExplicitPort)
        assertFalse(parsed.hasPortPlaceholder)
        assertEquals("root", parsed.username)
        assertEquals(8080, parsed.detectedLocalPort)
        assertEquals(8080, parsed.detectedRemotePort)
    }

    @Test
    fun testParseVastFormatWithPlaceholderPort() {
        // When user pastes template: ssh -p port root@x.x.x.x -L 8080:localhost:8080
        val input = "ssh -p port root@192.0.2.1 -L 8080:localhost:8080"
        val parsed = SshCommandParser.parse(input)

        assertEquals("192.0.2.1", parsed.host)
        assertTrue(parsed.hasPortPlaceholder)
        assertFalse(parsed.hasExplicitPort)
        assertEquals(8080, parsed.detectedRemotePort)
    }

    @Test
    fun testParseAttachedPortAndOptionFormat() {
        val attached = SshCommandParser.parse("ssh -p22022 root@194.26.196.12")
        assertEquals(22022, attached.port)
        assertTrue(attached.hasExplicitPort)

        val optionFormat = SshCommandParser.parse("ssh -o Port=18492 root@gpu.cloud.io")
        assertEquals(18492, optionFormat.port)
        assertTrue(optionFormat.hasExplicitPort)
    }

    @Test
    fun testParseSshUri() {
        val input = "ssh://ubuntu@ec2-54-211-12-34.compute-1.amazonaws.com:2222"
        val parsed = SshCommandParser.parse(input)

        assertEquals("ec2-54-211-12-34.compute-1.amazonaws.com", parsed.host)
        assertEquals(2222, parsed.port)
        assertTrue(parsed.hasExplicitPort)
        assertEquals("ubuntu", parsed.username)
    }

    @Test
    fun testPrivateKeyValidation() {
        val samplePem = "-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----"
        assertTrue(SshKeyHelper.isValidPrivateKey(samplePem))
    }

    @Test
    fun testGenerateAndLoadJSchKeys() {
        val rsaResult = SshKeyHelper.generateKeyPair("RSA")
        assertNotNull(rsaResult.privateKeyPem)
        assertNotNull(rsaResult.publicKeyOpenSsh)
        assertTrue(rsaResult.privateKeyPem.contains("BEGIN RSA PRIVATE KEY"))
        assertTrue(rsaResult.publicKeyOpenSsh.startsWith("ssh-rsa"))

        // Verify that JSch can load this generated key without any ASN1 error
        val jsch = com.jcraft.jsch.JSch()
        val keyBytes = rsaResult.privateKeyPem.toByteArray(Charsets.UTF_8)
        val pubBytes = rsaResult.publicKeyOpenSsh.toByteArray(Charsets.UTF_8)
        jsch.addIdentity("test_rsa", keyBytes, pubBytes, null)
        val identities = jsch.identityRepository.identities
        assertEquals(1, identities.size)
    }

    @Test
    fun testNoExistingLCreatesPortMirror() {
        // If somebody hands us a working SSH connection with no -L:
        val input = "ssh -p 12345 root@103.158.243.210"
        val aug = SshCommandParser.augmentCommandWithProxyPort(input, 7860)

        assertFalse(aug.hasExistingL)
        assertEquals("ssh -p 12345 root@103.158.243.210 -L 7860:localhost:7860", aug.effectiveCommand)
        assertEquals(1, aug.allForwardings.size)
        assertEquals(7860, aug.allForwardings[0].remotePort)
    }

    @Test
    fun testExistingLAddsPortMirror() {
        // If there's a -L, then there's already a port mirroring going on. We just add ours to it.
        val input = "ssh -p 34567 root@ssh4.vast.ai -L 8080:localhost:8080"
        val aug = SshCommandParser.augmentCommandWithProxyPort(input, 7860)

        assertTrue(aug.hasExistingL)
        assertEquals(1, aug.existingForwardings.size)
        assertEquals(8080, aug.existingForwardings[0].remotePort)
        assertEquals("ssh -p 34567 root@ssh4.vast.ai -L 8080:localhost:8080 -L 7860:localhost:7860", aug.effectiveCommand)
        assertEquals(2, aug.allForwardings.size)
        assertEquals(8080, aug.allForwardings[0].remotePort)
        assertEquals(7860, aug.allForwardings[1].remotePort)
    }

    @Test
    fun testExactUserVastCommandSlapsLOnEnd() {
        val input = "ssh -p 40946 root@95.169.14.15 -L 8080:localhost:8080"
        val parsed = SshCommandParser.parse(input)

        assertEquals("95.169.14.15", parsed.host)
        assertEquals(40946, parsed.port)
        assertTrue(parsed.hasExplicitPort)
        assertEquals("root", parsed.username)
        assertEquals(8080, parsed.detectedLocalPort)
        assertEquals(8080, parsed.detectedRemotePort)

        val aug = SshCommandParser.augmentCommandWithProxyPort(input, 7860)
        assertTrue(aug.hasExistingL)
        // Literally slaps -L on the end of whatever was given
        assertEquals("ssh -p 40946 root@95.169.14.15 -L 8080:localhost:8080 -L 7860:localhost:7860", aug.effectiveCommand)
        assertEquals(2, aug.allForwardings.size)
        assertEquals(8080, aug.allForwardings[0].remotePort)
        assertEquals(7860, aug.allForwardings[1].remotePort)
    }

    @Test
    fun testExactUserCommandWithNonBreakingSpaces() {
        val inputWithNbsp = "ssh\u00A0-p\u00A040946\u00A0root@95.169.14.15\u00A0-L\u00A08080:localhost:8080"
        val parsed = SshCommandParser.parse(inputWithNbsp)

        assertEquals("95.169.14.15", parsed.host)
        assertEquals(40946, parsed.port)
        assertTrue(parsed.hasExplicitPort)

        val aug = SshCommandParser.augmentCommandWithProxyPort(inputWithNbsp, 7860)
        assertTrue(aug.hasExistingL)
        assertEquals("ssh -p 40946 root@95.169.14.15 -L 8080:localhost:8080 -L 7860:localhost:7860", aug.effectiveCommand)
    }

    @Test
    fun testQuotedSshCommandPreservesPortAndSlapsLOnEnd() {
        val quoted = "\"ssh -p 40946 root@95.169.14.15 -L 8080:localhost:8080\""
        val parsed = SshCommandParser.parse(quoted)

        assertEquals("95.169.14.15", parsed.host)
        assertEquals(40946, parsed.port)

        val aug = SshCommandParser.augmentCommandWithProxyPort(quoted, 7860)
        assertTrue(aug.hasExistingL)
        assertEquals("ssh -p 40946 root@95.169.14.15 -L 8080:localhost:8080 -L 7860:localhost:7860", aug.effectiveCommand)
    }
}

