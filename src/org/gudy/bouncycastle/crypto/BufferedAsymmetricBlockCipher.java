package org.gudy.bouncycastle.crypto;

import org.gudy.bouncycastle.crypto.AsymmetricBlockCipher;
import org.gudy.bouncycastle.crypto.CipherParameters;
import org.gudy.bouncycastle.crypto.DataLengthException;
import org.gudy.bouncycastle.crypto.InvalidCipherTextException;

/**
 * a buffer wrapper for an asymmetric block cipher, allowing input
 * to be accumulated in a piecemeal fashion until final processing.
 */
public class BufferedAsymmetricBlockCipher
{
	protected byte[]        buf;
	protected int           bufOff;

    private boolean                 forEncryption;
    private AsymmetricBlockCipher   cipher;

    /**
     * base constructor.
     *
     * @param cipher the cipher this buffering object wraps.
     */
	public BufferedAsymmetricBlockCipher(
        AsymmetricBlockCipher     cipher) {
        this.cipher = cipher;
	}

    /**
     * return the underlying cipher for the buffer.
     *
     * @return the underlying cipher for the buffer.
     */
    public AsymmetricBlockCipher getUnderlyingCipher()
    {
        return cipher;
    }

    /**
     * return the amount of data sitting in the buffer.
     *
     * @return the amount of data sitting in the buffer.
     */
    public int getBufferPosition()
    {
        return bufOff;
    }

    /**
     * initialise the buffer and the underlying cipher.
     *
     * @param forEncryption if true the cipher is initialised for
     *  encryption, if false for decryption.
     * @param param the key and other data required by the cipher.
     */
    public void init(
        boolean             forEncryption,
        CipherParameters    params)
    {
        this.forEncryption = forEncryption;

        reset();

        cipher.init(forEncryption, params);

        buf = new byte[cipher.getInputBlockSize()];
        bufOff = 0;
    }

    /**
     * returns the largest size an input block can be.
     *
     * @return maximum size for an input block.
     */
	public int getInputBlockSize() {
		return cipher.getInputBlockSize();
	}

    /**
     * returns the maximum size of the block produced by this cipher.
     *
     * @return maximum size of the output block produced by the cipher.
     */
	public int getOutputBlockSize() {
		return cipher.getOutputBlockSize();
	}

    /**
     * add another byte for processing.
     *
     * @param in the input byte.
     */
	public void processByte(
        byte        in)
    {
        if (bufOff > buf.length)
        {
            throw new DataLengthException("attempt to process message to long for cipher");
        }

        buf[bufOff++] = in;
    }

    /**
     * add len bytes to the buffer for processing.
     *
     * @param in the input data
     * @param inOff offset into the in array where the data starts
     * @param len the length of the block to be processed.
     */
	public void processBytes(
		byte[]      in,
		int         inOff,
		int         len) {
        if (len == 0)
        {
            return;
        }

        if (len < 0)
        {
            throw new IllegalArgumentException("Can't have a negative input length!");
        }

        if (bufOff + len > buf.length)
        {
            throw new DataLengthException("attempt to process message to long for cipher");
        }

        System.arraycopy(in, inOff, buf, bufOff, len);
        bufOff += len;
	}

    /**
     * process the contents of the buffer using the underlying
     * cipher.
     *
     * @return the result of the encryption/decryption process on the
     * buffer.
     * @exception InvalidCipherTextException if we are given a garbage block.
     */
	public byte[] doFinal()
        throws InvalidCipherTextException
	{
        byte[] out = cipher.processBlock(buf, 0, bufOff);

        reset();

        return out;
	}

	/**
	 * Reset the buffer and the underlying cipher.
	 */
	public void reset() {
        /*
         * clean the buffer.
         */
        if (buf != null)
        {
            for (int i = 0; i < buf.length; i++)
            {
                buf[0] = 0;
            }
        }

        bufOff = 0;
	}
}
