package org.proninyaroslav.libretorrent.core.btn;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BtnPowCaptchaTest {

    private boolean valid(byte[] challenge, byte[] nonce, int bits, String algorithm)
            throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        digest.update(challenge);
        digest.update(nonce);
        return BtnPowCaptcha.hasLeadingZeroBits(digest.digest(), bits);
    }

    @Test
    public void solve_producesLeadingZeroBits_sha256() throws Exception {
        byte[] challenge = "btn-pow-challenge".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] nonce = BtnPowCaptcha.solve(challenge, 12, "SHA-256");
        assertEquals8(nonce);
        assertTrue(valid(challenge, nonce, 12, "SHA-256"));
    }

    @Test
    public void solve_higherDifficulty_sha512() throws Exception {
        byte[] challenge = new byte[]{1, 2, 3, 4, 5};
        byte[] nonce = BtnPowCaptcha.solve(challenge, 16, "SHA-512");
        assertEquals8(nonce);
        assertTrue(valid(challenge, nonce, 16, "SHA-512"));
    }

    @Test
    public void solve_zeroDifficulty_returnsZeroNonce() throws Exception {
        byte[] nonce = BtnPowCaptcha.solve(new byte[]{9}, 0, "SHA-256");
        assertEquals8(nonce);
        assertArrayEquals(new byte[8], nonce);
    }

    @Test
    public void solve_unknownAlgorithm_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> BtnPowCaptcha.solve(new byte[]{1}, 8, "NOT-A-HASH"));
    }

    @Test
    public void leadingZeroBits_correct() {
        byte[] h = new byte[]{0x00, 0x0F, (byte) 0xFF};
        assertTrue(BtnPowCaptcha.hasLeadingZeroBits(h, 0));
        assertTrue(BtnPowCaptcha.hasLeadingZeroBits(h, 4)); // 0x0F top nibble zero
        assertTrue(BtnPowCaptcha.hasLeadingZeroBits(h, 8));
        assertTrue(!BtnPowCaptcha.hasLeadingZeroBits(h, 13)); // 13th bit (0x08 of 0x0F) is set
        byte[] all = new byte[]{(byte) 0xFF};
        assertTrue(!BtnPowCaptcha.hasLeadingZeroBits(all, 1));
    }

    private static void assertEquals8(byte[] nonce) {
        org.junit.Assert.assertEquals("nonce must be 8 bytes", 8, nonce.length);
        // Touch ByteBuffer to keep the import meaningful in javadoc-free tests
        ByteBuffer.wrap(nonce).getLong();
    }
}
