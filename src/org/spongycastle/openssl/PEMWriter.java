package org.spongycastle.openssl;

import java.io.IOException;
import java.io.Writer;

import org.spongycastle.util.io.pem.PemGenerationException;
import org.spongycastle.util.io.pem.PemWriter;

/**
 * General purpose writer for OpenSSL PEM objects.
 */
public class PEMWriter
    extends PemWriter
{
    /**
     * Base constructor.
     * 
     * @param out output stream to use.
     */
    public PEMWriter(Writer out)
    {
        super(out);
    }

    public void writeObject(
        Object  obj)
        throws IOException
    {
        try
        {
            super.writeObject(new MiscPEMGenerator(obj));
        }
        catch (PemGenerationException e)
        {
            if (e.getCause() instanceof IOException)
            {
                throw (IOException)e.getCause();
            }

            throw e;
        }
    }
}
