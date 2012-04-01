package org.spongycastle.openssl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.security.cert.CertificateFactory;
import java.util.HashMap;
import java.util.Map;

import org.spongycastle.util.io.pem.PemObject;
import org.spongycastle.util.io.pem.PemObjectParser;
import org.spongycastle.util.io.pem.PemReader;

/**
 * Class for reading OpenSSL PEM encoded streams containing
 * X509 certificates, PKCS8 encoded keys and PKCS7 objects.
 * <p>
 * In the case of PKCS7 objects the reader will return a CMS ContentInfo object. Keys and
 * Certificates will be returned using the appropriate java.security type (KeyPair, PublicKey, X509Certificate,
 * or X509CRL). In the case of a Certificate Request a PKCS10CertificationRequest will be returned.
 * </p>
 */
public class PEMReader
    extends PemReader
{
    private final Map<String, PemObjectParser> parsers = new HashMap<String, PemObjectParser>();

    /**
     * Create a new PEMReader
     *
     * @param reader the Reader
     */
    public PEMReader(
        Reader reader)
    {
        this(reader, "BC");
    }
    
    /**
     * Create a new PEMReader with a password finder and differing providers for secret and public key
     * operations.
     *
     * @param reader   the Reader
     * @param pFinder  the password finder
     * @param symProvider  provider to use for symmetric operations
     * @param asymProvider provider to use for asymmetric (public/private key) operations
     */
    public PEMReader(
        Reader reader,
        String asymProvider)
    {
        super(reader);

        parsers.put("CERTIFICATE", new X509CertificateParser(asymProvider));
        parsers.put("X509 CERTIFICATE", new X509CertificateParser(asymProvider));
    }

    public Object readObject()
        throws IOException
    {
        PemObject obj = readPemObject();

        if (obj != null)
        {
            String type = obj.getType();
            if (parsers.containsKey(type))
            {
                return ((PemObjectParser)parsers.get(type)).parseObject(obj);
            }
            else
            {
                throw new IOException("unrecognised object: " + type);
            }
        }

        return null;
    }

    private class X509CertificateParser
        implements PemObjectParser
    {
        private String provider;

        public X509CertificateParser(String provider)
        {
            this.provider = provider;
        }

        /**
         * Reads in a X509Certificate.
         *
         * @return the X509Certificate
         * @throws IOException if an I/O error occured
         */
        public Object parseObject(PemObject obj)
            throws IOException
        {
            ByteArrayInputStream bIn = new ByteArrayInputStream(obj.getContent());

            try
            {
                CertificateFactory certFact
                    = CertificateFactory.getInstance("X.509", provider);

                return certFact.generateCertificate(bIn);
            }
            catch (Exception e)
            {
                throw new PEMException("problem parsing cert: " + e.toString(), e);
            }
        }
    }
}
