package org.gudy.bouncycastle.crypto.params;

import java.math.BigInteger;

import org.gudy.bouncycastle.crypto.params.AsymmetricKeyParameter;

public class RSAKeyParameters
    extends AsymmetricKeyParameter
{
    private BigInteger      modulus;
    private BigInteger      exponent;

    public RSAKeyParameters(
        boolean     isPrivate,
        BigInteger  modulus,
        BigInteger  exponent)
    {
        super(isPrivate);

        this.modulus = modulus;
        this.exponent = exponent;
    }

    public BigInteger getModulus()
    {
        return modulus;
    }

    public BigInteger getExponent()
    {
        return exponent;
    }
}
