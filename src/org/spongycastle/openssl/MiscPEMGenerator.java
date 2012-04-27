package org.spongycastle.openssl;

import java.io.IOException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import org.spongycastle.util.io.pem.PemGenerationException;
import org.spongycastle.util.io.pem.PemObject;
import org.spongycastle.util.io.pem.PemObjectGenerator;

/**
 * PEM generator for the original set of PEM objects used in Open SSL.
 */
public class MiscPEMGenerator
    implements PemObjectGenerator
{
    private Object obj;
    public MiscPEMGenerator(Object o)
    {
        this.obj = o;
    }

    private PemObject createPemObject(Object o)
        throws IOException
    {
        String  type;
        byte[]  encoding;

        if (o instanceof PemObject)
        {
            return (PemObject)o;
        }
        if (o instanceof PemObjectGenerator)
        {
            return ((PemObjectGenerator)o).generate();
        }
        if (o instanceof X509Certificate)
        {
            type = "CERTIFICATE";
            try
            {
                encoding = ((X509Certificate)o).getEncoded();
            }
            catch (CertificateEncodingException e)
            {
                throw new PemGenerationException("Cannot encode object: " + e.toString());
            }
        }
        else if (o instanceof PublicKey)
        {
            type = "PUBLIC KEY";

            encoding = ((PublicKey)o).getEncoded();
        }
        else
        {
            throw new PemGenerationException("unknown object passed - can't encode.");
        }

        return new PemObject(type, encoding);
    }

    public PemObject generate()
        throws PemGenerationException
    {
        try
        {
            return createPemObject(obj);
        }
        catch (IOException e)
        {
            throw new PemGenerationException("encoding exception: " + e.getMessage(), e);
        }
    }
}
