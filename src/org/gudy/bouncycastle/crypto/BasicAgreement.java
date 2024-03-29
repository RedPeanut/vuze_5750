package org.gudy.bouncycastle.crypto;

import java.math.BigInteger;

import org.gudy.bouncycastle.crypto.CipherParameters;

/**
 * The basic interface that basic Diffie-Hellman implementations
 * conforms to.
 */
public interface BasicAgreement
{
    /**
     * initialise the agreement engine.
     */
    public void init(CipherParameters param);

    /**
     * given a public key from a given party calculate the next
     * message in the agreement sequence.
     */
    public BigInteger calculateAgreement(CipherParameters pubKey);
}
