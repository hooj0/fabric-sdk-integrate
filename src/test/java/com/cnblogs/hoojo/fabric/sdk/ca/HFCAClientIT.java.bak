package com.cnblogs.hoojo.fabric.sdk.ca;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.fabric_ca.sdk.HFCAClient.DEFAULT_PROFILE_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.DatatypeConverter;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.TBSCertList;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.openssl.PEMParser;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.Attribute;
import org.hyperledger.fabric_ca.sdk.EnrollmentRequest;
import org.hyperledger.fabric_ca.sdk.HFCAAffiliation;
import org.hyperledger.fabric_ca.sdk.HFCAAffiliation.HFCAAffiliationResp;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.HFCAIdentity;
import org.hyperledger.fabric_ca.sdk.HFCAInfo;
import org.hyperledger.fabric_ca.sdk.MockHFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.hyperledger.fabric_ca.sdk.exception.EnrollmentException;
import org.hyperledger.fabric_ca.sdk.exception.IdentityException;
import org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric_ca.sdk.exception.RevocationException;
import org.hyperledger.fabric_ca.sdk.helper.Config;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.cnblogs.hoojo.fabric.sdk.config.DefaultConfiguration;
import com.cnblogs.hoojo.fabric.sdk.model.OrganizationUser;
import com.cnblogs.hoojo.fabric.sdk.persistence.KeyValueFileStore;
import com.cnblogs.hoojo.fabric.sdk.util.TestUtils;
import com.google.gson.Gson;

@SuppressWarnings({ "all" })
public class HFCAClientIT {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static final String ADMIN_NAME = "admin";
    private static final String ADMIN_PW = "adminpw";
    private static final String ADMIN_ORG = "org1";
    private static final String USER_ORG = "Org2";
    private static final String USER_AFFILIATION = "org1.department1";
    private static final String ORG_NAME_1 = "peerOrg1";
    private static final String ORG_NAME_2 = "peerOrg2";

    private static DefaultConfiguration config = DefaultConfiguration.getConfig();

    private HFCAClient client;
    private KeyValueFileStore store;
    private OrganizationUser admin;

    private static CryptoSuite crypto;

    // 跟踪我们创建了多少测试用户
    private static int userCount = 0;

    // 所有测试用户的共同前缀（后缀将是当前用户数）
    // 注意我们包含时间戳，以便这些测试可以重复执行
    // 无需重新启动CA（因为您不能多次注册用户名！）
    private static String userNamePrefix = "user" + (System.currentTimeMillis() / 1000) + "_";

    @BeforeClass
    public static void init() throws Exception {
        out("\n\n\nRUNNING: HFCAClientEnrollIT.\n");

        //resetConfig();
        crypto = CryptoSuite.Factory.getCryptoSuite();
    }

    @Before
    public void setup() throws Exception {

        File storeFile = new File("HFCASampletest.properties");
        if (storeFile.exists()) { // For testing start fresh
            storeFile.delete();
        }
        store = new KeyValueFileStore(storeFile);
        storeFile.deleteOnExit();

        client = HFCAClient.createNewInstance(
                config.getOrganization(ORG_NAME_1).getCALocation(),
                config.getOrganization(ORG_NAME_1).getCAProperties());
        
        client.setCryptoSuite(crypto);

        // OrganizationUser can be any implementation that implements org.hyperledger.fabric.sdk.User Interface
        admin = store.getMember(ADMIN_NAME, ADMIN_ORG);
        if (!admin.isEnrolled()) { // Preregistered admin only needs to be enrolled with Fabric CA.
        	Enrollment enrollment = client.enroll(admin.getName(), ADMIN_PW);
        	out("admin enroll: %s", json(enrollment));
        	
            admin.setEnrollment(enrollment);
        }
    }

    // 注册用户和认证用户，添加额外的属性
    @Test
    public void testRegisterAttributes() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        String userName = "mrAttributes2";
        String password = "mrAttributespassword";

        OrganizationUser user = new OrganizationUser(userName, ADMIN_ORG, store);

        // 构建注册请求对象
        RegistrationRequest registrationRequest = new RegistrationRequest(user.getName(), USER_AFFILIATION);
        // 设置注册属性
        registrationRequest.addAttribute(new Attribute("testattr1", "mrAttributesValue1")); // 添加注册属性
        registrationRequest.addAttribute(new Attribute("testattr2", "mrAttributesValue2"));
        registrationRequest.addAttribute(new Attribute("testattrDEFAULTATTR", "mrAttributesValueDEFAULTATTR", true)); // 设置带默认值的属性
        
        // 设置秘钥
        registrationRequest.setSecret(password);
        out("register request: %s", json(registrationRequest));

        user.setEnrollmentSecret(client.register(registrationRequest, admin)); // 注册，返回秘钥
        if (!user.getEnrollmentSecret().equals(password)) {
            fail("Secret returned from RegistrationRequest not match : " + user.getEnrollmentSecret());
        }
        
        // 构建认证请求对象
        EnrollmentRequest enrollmentRequest = new EnrollmentRequest();
        enrollmentRequest.addAttrReq("testattr2").setOptional(false);

        // 认证，返回证书
        user.setEnrollment(client.enroll(user.getName(), user.getEnrollmentSecret(), enrollmentRequest)); // 添加额外的属性进行认证
        out("enroll ment request: %s", json(enrollmentRequest));

        Enrollment enrollment = user.getEnrollment();
        String cert = enrollment.getCert();
        String certdec = getStringCert(cert);
        out("cert: %s, key: %s", cert, enrollment.getKey());
        
        assertTrue(format("Missing testattr2 in certficate decoded: %s", certdec), certdec.contains("\"testattr2\":\"mrAttributesValue2\""));
        //Since request had specific attributes don't expect defaults.
        assertFalse(format("Contains testattrDEFAULTATTR in certificate decoded: %s", certdec), certdec.contains("\"testattrDEFAULTATTR\"")
                || certdec.contains("\"mrAttributesValueDEFAULTATTR\""));
        assertFalse(format("Contains testattr1 in certificate decoded: %s", certdec), certdec.contains("\"testattr1\"") || certdec.contains("\"mrAttributesValue1\""));
    }

    /**
     * 注册和认证用户。认证使用默认值，不设置额外属性
     * @throws Exception
     */
    @Test
    public void testRegisterAttributesDefault() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        String password = "mrAttributespassword";

        OrganizationUser user = new OrganizationUser("mrAttributesDefault", ADMIN_ORG, store);

        RegistrationRequest registrationRequest = new RegistrationRequest(user.getName(), USER_AFFILIATION);

        registrationRequest.addAttribute(new Attribute("testattr1", "mrAttributesValue1"));
        registrationRequest.addAttribute(new Attribute("testattr2", "mrAttributesValue2"));
        registrationRequest.addAttribute(new Attribute("testattrDEFAULTATTR", "mrAttributesValueDEFAULTATTR", true));
        
        registrationRequest.setSecret(password);
        user.setEnrollmentSecret(client.register(registrationRequest, admin)); // 注册
        if (!user.getEnrollmentSecret().equals(password)) {
            fail("Secret returned from RegistrationRequest not match : " + user.getEnrollmentSecret());
        }

        user.setEnrollment(client.enroll(user.getName(), user.getEnrollmentSecret())); // 认证，默认值

        Enrollment enrollment = user.getEnrollment();
        String cert = enrollment.getCert();
        String certdec = getStringCert(cert);

        assertTrue(format("Missing testattrDEFAULTATTR in certficate decoded: %s", certdec), certdec.contains("\"testattrDEFAULTATTR\":\"mrAttributesValueDEFAULTATTR\""));
        //Since request and no attribute requests at all defaults should be in certificate.

        assertFalse(format("Contains testattr1 in certificate decoded: %s", certdec), certdec.contains("\"testattr1\"") || certdec.contains("\"mrAttributesValue1\""));
        assertFalse(format("Contains testattr2 in certificate decoded: %s", certdec), certdec.contains("\"testattr2\"") || certdec.contains("\"mrAttributesValue2\""));
    }

    /**
     * 认证用户使用空的请求选项，不带属性
     */
    @Test
    public void testRegisterAttributesNONE() throws Exception {
    	
    	String password = "mrAttributespassword";

    	OrganizationUser user = new OrganizationUser("mrAttributesNone", ADMIN_ORG, store);

        RegistrationRequest rr = new RegistrationRequest(user.getName(), USER_AFFILIATION);

        rr.addAttribute(new Attribute("testattr1", "mrAttributesValue1"));
        rr.addAttribute(new Attribute("testattr2", "mrAttributesValue2"));
        rr.addAttribute(new Attribute("testattrDEFAULTATTR", "mrAttributesValueDEFAULTATTR", true));
        
        rr.setSecret(password);
        
        user.setEnrollmentSecret(client.register(rr, admin));
        if (!user.getEnrollmentSecret().equals(password)) {
            fail("Secret returned from RegistrationRequest not match : " + user.getEnrollmentSecret());
        }

        EnrollmentRequest req = new EnrollmentRequest();
        req.addAttrReq(); // empty ensure no attributes.

        user.setEnrollment(client.enroll(user.getName(), user.getEnrollmentSecret(), req)); // 认证，不带属性

        Enrollment enrollment = user.getEnrollment();
        String cert = enrollment.getCert();
        String certdec = getStringCert(cert);

        assertFalse(format("Contains testattrDEFAULTATTR in certificate decoded: %s", certdec),
                certdec.contains("\"testattrDEFAULTATTR\"") || certdec.contains("\"mrAttributesValueDEFAULTATTR\""));
        assertFalse(format("Contains testattr1 in certificate decoded: %s", certdec), certdec.contains("\"testattr1\"") || certdec.contains("\"mrAttributesValue1\""));
        assertFalse(format("Contains testattr2 in certificate decoded: %s", certdec), certdec.contains("\"testattr2\"") || certdec.contains("\"mrAttributesValue2\""));

    }

    private static final Pattern compile = Pattern.compile("^-----BEGIN CERTIFICATE-----$" + "(.*?)" + "\n-----END CERTIFICATE-----\n", Pattern.DOTALL | Pattern.MULTILINE);

    private static String getStringCert(String pemFormat) {
        String ret = null;

        final Matcher matcher = compile.matcher(pemFormat);
        if (matcher.matches()) {
            final String base64part = matcher.group(1).replaceAll("\n", "");
            Base64.Decoder b64dec = Base64.getDecoder();
            ret = new String(b64dec.decode(base64part.getBytes(UTF_8)));
        } else {
            fail("Certificate failed to match expected pattern. Certificate:\n" + pemFormat);
        }

        return ret;
    }

    // 测试重新注册已注销注册的用户
    @Test
    public void testReenrollAndRevoke() throws Exception {

        OrganizationUser user = generatorUser(ADMIN_ORG);
        out("user: %s", json(user));

        if (!user.isRegistered()) { // 没有注册的用户，进行 registered 
        	String password = "testReenrollAndRevoke";

        	RegistrationRequest rr = new RegistrationRequest(user.getName(), USER_AFFILIATION);
            rr.setSecret(password);
            
            user.setEnrollmentSecret(client.register(rr, admin));
            if (!user.getEnrollmentSecret().equals(password)) {
                fail("Secret returned from RegistrationRequest not match : " + user.getEnrollmentSecret());
            }
        }
        
        if (!user.isEnrolled()) { // 没有认证的用户进行认证
            user.setEnrollment(client.enroll(user.getName(), user.getEnrollmentSecret()));
        }

        sleepALittle();

        // get another enrollment
        EnrollmentRequest req = new EnrollmentRequest(DEFAULT_PROFILE_NAME, "label 1", null);
        req.addHost("example1.ibm.com"); // 添加Host
        req.addHost("example2.ibm.com");
        
        Enrollment tmpEnroll = client.reenroll(user, req); // 重新注册用户

        // verify
        String cert = tmpEnroll.getCert();
        verifyOptions(cert, req);

        sleepALittle();

        // 撤销用户的注册
        client.revoke(admin, tmpEnroll, "remove user 2");

        // 试图重新注册应该没问题（上面的撤销仅限于此用户的特定注册）
        tmpEnroll = client.reenroll(user);
        cert = tmpEnroll.getCert();
        out("cert: %s", cert);
    }

    // 试图重新注册已撤销用户的测试
    @Test
    public void testUserRevoke() throws Exception {

        thrown.expect(EnrollmentException.class);
        thrown.expectMessage("Failed to re-enroll user");

        Calendar calendar = Calendar.getInstance(); // gets a calendar using the default time zone and locale.
        Date revokedTinyBitAgoTime = calendar.getTime(); //avoid any clock skewing.

        OrganizationUser user = generatorUser(USER_ORG);

        if (!user.isRegistered()) {
        	String password = "testUserRevoke";

        	RegistrationRequest rr = new RegistrationRequest(user.getName(), USER_AFFILIATION);
            
        	rr.setSecret(password);
            rr.addAttribute(new Attribute("user.role", "department lead"));
            rr.addAttribute(new Attribute(HFCAClient.HFCA_ATTRIBUTE_HFREVOKER, "true"));
            
            user.setEnrollmentSecret(client.register(rr, admin)); // Admin can register other users.
            if (!user.getEnrollmentSecret().equals(password)) {
                fail("Secret returned from RegistrationRequest not match : " + user.getEnrollmentSecret());
            }
        }

        if (!user.isEnrolled()) {
            EnrollmentRequest req = new EnrollmentRequest(DEFAULT_PROFILE_NAME, "label 2", null);
            req.addHost("example3.ibm.com");
            
            user.setEnrollment(client.enroll(user.getName(), user.getEnrollmentSecret(), req));

            // verify
            String cert = user.getEnrollment().getCert();
            verifyOptions(cert, req);
        }

        int startedWithRevokes = -1;

        if (!config.isRunningAgainstFabric10()) {
            Thread.sleep(1000); //prevent clock skewing. make sure we request started with revokes.
            startedWithRevokes = getRevokes(null).length; // 生成证书撤销列表
            out("已撤销证书数量startedWithRevokes：%d", startedWithRevokes);
            
            Thread.sleep(1000); //prevent clock skewing. make sure we request started with revokes.
        }

        // 通过用户名撤销用户证书
        // admin -> 具有在CA服务器中配置的revoker属性的admin用户
        // userName -> 被撤销证书的用户
        // reason -> 撤销原因，请参阅RFC 5280
        client.revoke(admin, user.getName(), "revoke user 3"); // 撤销用户所有证书（包括他所有的注册认证）
        if (!config.isRunningAgainstFabric10()) {
            final int newRevokes = getRevokes(null).length; // 比上面撤销的数量 + 1，因为 revoke 撤销了 刚认证的新用户
            out("已撤销证书数量newRevokes：%d", newRevokes);

            assertEquals(format("Expected one more revocation %d, but got %d", startedWithRevokes + 1, newRevokes), startedWithRevokes + 1, newRevokes);

            // see if we can get right number of revokes that we started with by specifying the time: revokedTinyBitAgoTime
            // TODO: Investigate clock scew
            // final int revokestinybitago = getRevokes(revokedTinyBitAgoTime).length; //Should be same number when test case was started.
            // assertEquals(format("Expected same revocations %d, but got %d", startedWithRevokes, revokestinybitago), startedWithRevokes, revokestinybitago);
        }

        // trying to reenroll the revoked user should fail with an EnrollmentException
        client.reenroll(user); // 试图注册撤销的用户，但抛出EnrollmentException异常
    }

    // 测试撤销证书，撤销用户的所有注册
    @Test
    public void testCertificateRevoke() throws Exception {

        OrganizationUser user = generatorUser(USER_ORG);

        if (!user.isRegistered()) {
            RegistrationRequest rr = new RegistrationRequest(user.getName(), USER_AFFILIATION);
            
            String password = "testUserRevoke";
            rr.setSecret(password);
            rr.addAttribute(new Attribute("user.role", "department lead"));
            rr.addAttribute(new Attribute(HFCAClient.HFCA_ATTRIBUTE_HFREVOKER, "true"));
            
            
            user.setEnrollmentSecret(client.register(rr, admin)); // Admin can register other users.
            if (!user.getEnrollmentSecret().equals(password)) {
                fail("Secret returned from RegistrationRequest not match : " + user.getEnrollmentSecret());
            }
        }

        if (!user.isEnrolled()) {
            EnrollmentRequest req = new EnrollmentRequest(DEFAULT_PROFILE_NAME, "label 2", null);
            req.addHost("example3.ibm.com");
            
            user.setEnrollment(client.enroll(user.getName(), user.getEnrollmentSecret(), req));
        }

        // verify
        String cert = user.getEnrollment().getCert();
        out("cert: %s", cert);

        BufferedInputStream pem = new BufferedInputStream(new ByteArrayInputStream(cert.getBytes()));
        CertificateFactory certFactory = CertificateFactory.getInstance(Config.getConfig().getCertificateFormat());
        X509Certificate certificate = (X509Certificate) certFactory.generateCertificate(pem);

        // get its serial number
        String serial = DatatypeConverter.printHexBinary(certificate.getSerialNumber().toByteArray());
        out("serial: %s", serial);
        
        // get its aki
        // 2.5.29.35 : AuthorityKeyIdentifier
        byte[] extensionValue = certificate.getExtensionValue(Extension.authorityKeyIdentifier.getId());
        ASN1OctetString akiOc = ASN1OctetString.getInstance(extensionValue);
        String aki = DatatypeConverter.printHexBinary(AuthorityKeyIdentifier.getInstance(akiOc.getOctets()).getKeyIdentifier());
        out("aki: %s", aki);
        
        int startedWithRevokes = -1;

        if (!config.isRunningAgainstFabric10()) {
            Thread.sleep(1000); //prevent clock skewing. make sure we request started with revokes.
            startedWithRevokes = getRevokes(null).length; //one more after we do this revoke.
            out("已撤销证书数量startedWithRevokes：%d", startedWithRevokes);
            Thread.sleep(1000); //prevent clock skewing. make sure we request started with revokes.
        }

        // 撤销此用户的所有注册
        // admin -> 具有在CA服务器中配置的revoker属性的admin用户
        // serial -> 要撤销的证书的序列号
        // aki -> 要撤销证书的aki
        // reason -> 撤销原因，请参阅RFC 5280
        client.revoke(admin, serial, aki, "revoke certificate"); // 撤销用户证书
        if (!config.isRunningAgainstFabric10()) {
            final int newRevokes = getRevokes(null).length;
            out("已撤销证书数量startedWithRevokes：%d", newRevokes);
            
            assertEquals(format("Expected one more revocation %d, but got %d", startedWithRevokes + 1, newRevokes), startedWithRevokes + 1, newRevokes);
        }
    }

    // 测试试图撤销具有空理由的用户
    @Test
    public void testUserRevokeNullReason() throws Exception {

        thrown.expect(EnrollmentException.class);
        thrown.expectMessage("Failed to re-enroll user");

        Calendar calendar = Calendar.getInstance(); // gets a calendar using the default time zone and locale.
        calendar.add(Calendar.SECOND, -1);
        
        Date revokedTinyBitAgoTime = calendar.getTime(); //avoid any clock skewing.

        OrganizationUser user = generatorUser(USER_ORG);

        if (!user.isRegistered()) {
            RegistrationRequest rr = new RegistrationRequest(user.getName(), USER_AFFILIATION);
            String password = "testUserRevoke";
            rr.setSecret(password);
            rr.addAttribute(new Attribute("user.role", "department lead"));
            rr.addAttribute(new Attribute(HFCAClient.HFCA_ATTRIBUTE_HFREVOKER, "true"));
            user.setEnrollmentSecret(client.register(rr, admin)); // Admin can register other users.
            if (!user.getEnrollmentSecret().equals(password)) {
                fail("Secret returned from RegistrationRequest not match : " + user.getEnrollmentSecret());
            }
        }

        sleepALittle();

        if (!user.isEnrolled()) {
            EnrollmentRequest req = new EnrollmentRequest(DEFAULT_PROFILE_NAME, "label 2", null);
            req.addHost("example3.ibm.com");
            user.setEnrollment(client.enroll(user.getName(), user.getEnrollmentSecret(), req));

            // verify
            String cert = user.getEnrollment().getCert();
            verifyOptions(cert, req);
        }

        sleepALittle();

        int startedWithRevokes = -1;
        if (!config.isRunningAgainstFabric10()) {
            startedWithRevokes = getRevokes(null).length; //one more after we do this revoke.
        }

        // revoke all enrollment of this user
        client.revoke(admin, user.getName(), null); // reason = null，无理由撤销用户证书
        
        if (!config.isRunningAgainstFabric10()) {
            final int newRevokes = getRevokes(null).length;
            assertEquals(format("Expected one more revocation %d, but got %d", startedWithRevokes + 1, newRevokes), startedWithRevokes + 1, newRevokes);
        }

        // trying to reenroll the revoked user should fail with an EnrollmentException
        client.reenroll(user); // 试图注册撤销的用户，但抛出EnrollmentException异常
    }

    // 使用revoke API 使用genCRL 撤销用户的测试
	@Test
    public void testUserRevokeGenCRL() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        thrown.expect(EnrollmentException.class);
        thrown.expectMessage("Failed to re-enroll user");

        Calendar calendar = Calendar.getInstance(); // gets a calendar using the default time zone and locale.
        calendar.add(Calendar.SECOND, -1);
        Date revokedTinyBitAgoTime = calendar.getTime(); //avoid any clock skewing.

        OrganizationUser user1 = generatorUser(USER_ORG);
        OrganizationUser user2 = generatorUser(USER_ORG);

        OrganizationUser[] users = new OrganizationUser[] {user1, user2};

        for (OrganizationUser user : users) {
            if (!user.isRegistered()) {
                RegistrationRequest rr = new RegistrationRequest(user.getName(), USER_AFFILIATION);
                
                String password = "testUserRevoke";
                rr.setSecret(password);
                rr.addAttribute(new Attribute("user.role", "department lead"));
                rr.addAttribute(new Attribute(HFCAClient.HFCA_ATTRIBUTE_HFREVOKER, "true"));
                
                user.setEnrollmentSecret(client.register(rr, admin)); // Admin can register other users.
                if (!user.getEnrollmentSecret().equals(password)) {
                    fail("Secret returned from RegistrationRequest not match : " + user.getEnrollmentSecret());
                }
            }

            sleepALittle();

            if (!user.isEnrolled()) {
                EnrollmentRequest req = new EnrollmentRequest(DEFAULT_PROFILE_NAME, "label 2", null);
                req.addHost("example3.ibm.com");
                user.setEnrollment(client.enroll(user.getName(), user.getEnrollmentSecret(), req));

                // verify
                String cert = user.getEnrollment().getCert();
                verifyOptions(cert, req);
            }
        }

        sleepALittle();

        int startedWithRevokes = -1;

        startedWithRevokes = getRevokes(null).length; //one more after we do this revoke.
        out("startedWithRevokes: %s", startedWithRevokes); // N
        
        // 撤销此用户的所有注册并请求返回CRL
        String crl = client.revoke(admin, user1.getName(), null, true); // 撤销一个用户（包括他所有的认证证书）
        out("crl: %s", crl);
        assertNotNull("Failed to get CRL using the Revoke API", crl);

        final int newRevokes = getRevokes(null).length;
        out("newRevokes: %s", newRevokes); // N + 1
        assertEquals(format("Expected one more revocation %d, but got %d", startedWithRevokes + 1, newRevokes), startedWithRevokes + 1, newRevokes);

        final int crlLength = parseCRL(crl).length;
        out("crlLength: %s", crlLength); // N + 1
        assertEquals(format("The number of revokes %d does not equal the number of revoked certificates (%d) in crl", newRevokes, crlLength), newRevokes, crlLength);

        // trying to reenroll the revoked user should fail with an EnrollmentException
        client.reenroll(user1); // 重新注册，发生异常

        String crl2 = client.revoke(admin, user2.getName(), null, false);
        out("crl2: %s", crl2);
        assertEquals("CRL not requested, CRL should be empty", "", crl2);
    }

    private TBSCertList.CRLEntry[] getRevokes(Date r) throws Exception {

    	// 生成证书撤销列表
    	// generateCRL 参数说明
    	// 参数1：管理员用户在CA服务器中配置
    	// 参数2：如果不为空，则限制在此日期之前返回到撤消的证书。
    	// 参数3：如果不为空，则限制在此日期之后返回撤销的证书。
    	// 参数4：如果不为空，则限制在该日期之前返回过期的证书。
    	// 参数5：如果不为空，则限制在此日期之后返回过期的证书。
    	
    	
        String crl = client.generateCRL(admin, r, null, null, null); //限制在此日期之前返回到撤消的证书
        out("crl: %s", crl);
        
        return parseCRL(crl);
    }

	private TBSCertList.CRLEntry[] parseCRL(String crl) throws Exception {

        Base64.Decoder b64dec = Base64.getDecoder();
        final byte[] decode = b64dec.decode(crl.getBytes(UTF_8));

        PEMParser pem = new PEMParser(new StringReader(new String(decode)));
        X509CRLHolder holder = (X509CRLHolder) pem.readObject();

        return holder.toASN1Structure().getRevokedCertificates();
    }

    // 测试获得身份
    @Test
    public void testCreateAndGetIdentity() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        HFCAIdentity ident = getIdentityReq("testuser1", HFCAClient.HFCA_TYPE_PEER); // 创建一个身份
        out("status: %s", ident.create(admin)); // admin 创建

        HFCAIdentity identGet = client.newHFCAIdentity(ident.getEnrollmentId());
        out("status: %s", identGet.read(admin)); // admin 读取
        
        out("EnrollmentId: %s, Type: %s, Affiliation: %s, MaxEnrollments: %s", identGet.getEnrollmentId(), identGet.getType(), identGet.getAffiliation(), identGet.getMaxEnrollments());
        
        assertEquals("Incorrect response for id", ident.getEnrollmentId(), identGet.getEnrollmentId());
        assertEquals("Incorrect response for type", ident.getType(), identGet.getType());
        assertEquals("Incorrect response for affiliation", ident.getAffiliation(), identGet.getAffiliation());
        assertEquals("Incorrect response for max enrollments", ident.getMaxEnrollments(), identGet.getMaxEnrollments());

        Collection<Attribute> attrs = identGet.getAttributes();
        Boolean found = false;
        for (Attribute attr : attrs) {
            if (attr.getName().equals("testattr1")) {
                found = true;
                break;
            }
        }

        if (!found) {
            fail("Incorrect response for attribute");
        }
    }

    // 测试获取不存在的身份
    @Test
    public void testGetIdentityNotExist() throws Exception {
        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        TestUtils.setField(client, "statusCode", 405);
        
        HFCAIdentity ident = client.newHFCAIdentity("fakeUser");
        int statusCode = ident.read(admin);
        
        out("statusCode: %s", statusCode);
        if (statusCode != 404) { // 没有找到 fakeUser
            fail("Incorrect status code return for an identity that is not found, should have returned 404 and not thrown an excpetion");
        }
        TestUtils.setField(client, "statusCode", 400);
    }

    // 测试获取所有身份
	@Test
	public void testGetAllIdentity() throws Exception {
		if (config.isRunningAgainstFabric10()) {
			return; // needs v1.1
		}
		
		String user = "testuser4";

		HFCAIdentity ident = getIdentityReq(user, HFCAClient.HFCA_TYPE_CLIENT);
		ident.create(admin);

		// 获取注册服务商被允许查看的所有身份
		Collection<HFCAIdentity> foundIdentities = client.getHFCAIdentities(admin);
		out("foundIdentities: %s", json(foundIdentities));
		
		String[] expectedIdenities = new String[] { user, "admin" };

		Integer found = 0;
		for (HFCAIdentity id : foundIdentities) {
			out("id: %s", json(id));
			
			for (String name : expectedIdenities) {
				if (id.getEnrollmentId().equals(name)) {
					found++;
				}
			}
		}

		if (found != 2) {
			fail("Failed to get the correct number of identities");
		}
	}

    // 测试修改身份
    @Test
    public void testModifyIdentity() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        HFCAIdentity ident = getIdentityReq("testuser5", HFCAClient.HFCA_TYPE_ORDERER);
        int statusCode = ident.create(admin);
        out("statusCode: %s", statusCode);
        
        assertEquals("Incorrect response for type", "orderer", ident.getType());
        assertNotEquals("Incorrect value for max enrollments", ident.getMaxEnrollments(), new Integer(5));

        ident.setMaxEnrollments(5);
        statusCode = ident.update(admin); // 修改身份
        out("statusCode: %s", statusCode);
        
        assertEquals("Incorrect value for max enrollments", ident.getMaxEnrollments(), new Integer(5));

        ident.setMaxEnrollments(100);
        statusCode = ident.read(admin);
        out("statusCode: %s", statusCode);
        
        assertEquals("Incorrect value for max enrollments", new Integer(5), ident.getMaxEnrollments());
    }

    // 测试删除身份
    @Test
    public void testDeleteIdentity() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        thrown.expect(IdentityException.class);
        thrown.expectMessage("Failed to get User");

        OrganizationUser user = new OrganizationUser("testuser6", ADMIN_ORG, store);

        HFCAIdentity ident = client.newHFCAIdentity(user.getName());

        ident.create(admin);
        System.out.println(ident.delete(admin)); // 删除身份

        ident.read(admin);
    }

    // 测试删除身份并确保删除后无法更新
    @Test
    public void testDeleteIdentityFailUpdate() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        thrown.expect(IdentityException.class);
        thrown.expectMessage("Identity has been deleted");

        HFCAIdentity ident = client.newHFCAIdentity("deletedUser");

        ident.create(admin);
        ident.delete(admin);

        ident.update(admin); // 删除后再次更新抛出异常
    }

    // 测试删除一个身份，并确保它不能再次删除
    @Test
    public void testDeleteIdentityFailSecondDelete() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        //thrown.expect(IdentityException.class);
        //thrown.expectMessage("Identity has been deleted");

        HFCAIdentity ident = client.newHFCAIdentity("deletedUser2");

        ident.create(admin);
        ident.delete(admin);

        ident.delete(admin); // 重复删除将发出异常
    }

    // 删除身份不允许删除的CA上的身份
    @Test
    public void testDeleteIdentityNotAllowed() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }
        
        thrown.expectMessage("Identity removal is disabled");

        OrganizationUser user = new OrganizationUser("testuser5", "org2", store);

        HFCAClient client2 = HFCAClient.createNewInstance(
                config.getOrganization(ORG_NAME_2).getCALocation(),
                config.getOrganization(ORG_NAME_2).getCAProperties());
        
        client2.setCryptoSuite(crypto);

        // OrganizationUser can be any implementation that implements org.hyperledger.fabric.sdk.User Interface
        OrganizationUser admin2 = store.getMember(ADMIN_NAME, "org2");
        if (!admin2.isEnrolled()) { // Preregistered admin only needs to be enrolled with Fabric CA.
            admin2.setEnrollment(client2.enroll(admin.getName(), ADMIN_PW)); // ca 认证
        }

        HFCAIdentity ident = client2.newHFCAIdentity(user.getName());

        ident.create(admin2); 
        ident.delete(admin2); // 删除一个已被CA 认证过的身份
    }

    // 获取 Affiliation
    @Test
    public void testGetAffiliation() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        HFCAAffiliation aff = client.newHFCAAffiliation("org2");
        int resp = aff.read(admin);
        
        out("name: %s", aff.getName());
        out("Child name: %s", aff.getChild("department1").getName());
        out("Child name: %s", json(aff.getChild("department1")));
        out("resp: %s", resp);

        assertEquals("Incorrect response for affiliation name", "org2", aff.getName());
        assertEquals("Incorrect response for child affiliation name", "org2.department1", aff.getChild("department1").getName());
        assertEquals("Incorrect status code", new Integer(200), new Integer(resp));
    }

    // 获取所有 affiliation
    @Test
    public void testGetAllAffiliation() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        HFCAAffiliation resp = client.getHFCAAffiliations(admin);

        ArrayList<String> expectedFirstLevelAffiliations = new ArrayList<String>(Arrays.asList("org2", "org1"));
        
        for (HFCAAffiliation aff : resp.getChildren()) {
        	out("child: %s", json(aff));
        	
            for (Iterator<String> iter = expectedFirstLevelAffiliations.iterator(); iter.hasNext();) {
                String element = iter.next();
                if (aff.getName().equals(element)) {
                    iter.remove();
                }
            }
        }

        if (!expectedFirstLevelAffiliations.isEmpty()) {
            fail("Failed to get the correct of affiliations, affiliations not returned: %s" + expectedFirstLevelAffiliations.toString());
        }

        ArrayList<String> expectedSecondLevelAffiliations = new ArrayList<String>(Arrays.asList("org2.department1", "org1.department1", "org1.department2"));
        for (HFCAAffiliation aff : resp.getChildren()) {
            for (HFCAAffiliation aff2 : aff.getChildren()) {
                expectedSecondLevelAffiliations.removeIf(element -> aff2.getName().equals(element));
            }
        }

        if (!expectedSecondLevelAffiliations.isEmpty()) {
            fail("Failed to get the correct child affiliations, affiliations not returned: %s" + expectedSecondLevelAffiliations.toString());
        }
    }

    // 添加 affiliation
    @Test
    public void testCreateAffiliation() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        HFCAAffiliation aff = client.newHFCAAffiliation("org3");
        HFCAAffiliationResp resp = aff.create(admin); // 创建 HFCAAffiliation

        assertEquals("Incorrect status code", new Integer(201), new Integer(resp.getStatusCode()));
        assertEquals("Incorrect response for id", "org3", aff.getName());

        Collection<HFCAAffiliation> children = aff.getChildren();
        assertEquals("Should have no children", 0, children.size());
    }

    // Tests updating an affiliation
    @Test
    public void testUpdateAffiliation() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        HFCAAffiliation aff = client.newHFCAAffiliation("org4");
        aff.create(admin);

        HFCAIdentity ident = client.newHFCAIdentity("testuser_org4");
        ident.setAffiliation(aff.getName());
        ident.create(admin);

        HFCAAffiliation aff2 = client.newHFCAAffiliation("org4.dept1");
        aff2.create(admin);

        HFCAIdentity ident2 = client.newHFCAIdentity("testuser_org4.dept1");
        ident2.setAffiliation("org4.dept1");
        ident2.create(admin);

        HFCAAffiliation aff3 = client.newHFCAAffiliation("org4.dept1.team1");
        aff3.create(admin);

        HFCAIdentity ident3 = client.newHFCAIdentity("testuser_org4.dept1.team1");
        ident3.setAffiliation("org4.dept1.team1");
        ident3.create(admin);

        aff.setUpdateName("org5");
        // Set force option to true, since their identities associated with affiliations
        // that are getting updated
        HFCAAffiliationResp resp = aff.update(admin, true); // 强制更新

        int found = 0;
        int idCount = 0;
        // Should contain the affiliations affected by the update request
        HFCAAffiliation child = aff.getChild("dept1");
        assertNotNull(child);
        assertEquals("Failed to get correct child affiliation", "org5.dept1", child.getName());
        for (HFCAIdentity id : child.getIdentities()) {
            if (id.getEnrollmentId().equals("testuser_org4.dept1")) {
                idCount++;
            }
        }
        
        HFCAAffiliation child2 = child.getChild("team1");
        assertNotNull(child2);
        assertEquals("Failed to get correct child affiliation", "org5.dept1.team1", child2.getName());
        for (HFCAIdentity id : child2.getIdentities()) {
            if (id.getEnrollmentId().equals("testuser_org4.dept1.team1")) {
                idCount++;
            }
        }

        for (HFCAIdentity id : aff.getIdentities()) {
            if (id.getEnrollmentId().equals("testuser_org4")) {
                idCount++;
            }
        }

        if (idCount != 3) {
            fail("Incorrect number of ids returned");
        }

        assertEquals("Incorrect response for id", "org5", aff.getName());
        assertEquals("Incorrect status code", new Integer(200), new Integer(resp.getStatusCode()));
    }

    // Tests updating an affiliation that doesn't require force option
    @Test
    public void testUpdateAffiliationNoForce() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        HFCAAffiliation aff = client.newHFCAAffiliation("org_5");
        aff.create(admin);
        aff.setUpdateName("org_6");
        
        HFCAAffiliationResp resp = aff.update(admin);

        assertEquals("Incorrect status code", new Integer(200), new Integer(resp.getStatusCode()));
        assertEquals("Failed to delete affiliation", "org_6", aff.getName());
    }

    // Trying to update affiliations with child affiliations and identities
    // should fail if not using 'force' option.
    @Test
    public void testUpdateAffiliationInvalid() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        thrown.expectMessage("Need to use 'force' to remove identities and affiliation");

        HFCAAffiliation aff = client.newHFCAAffiliation("org1.dept1");
        aff.create(admin);

        HFCAAffiliation aff2 = aff.createDecendent("team1"); //请求期间受影响的身份
        aff2.create(admin);

        HFCAIdentity ident = getIdentityReq("testorg1dept1", "client");
        ident.setAffiliation(aff.getName());
        ident.create(admin);

        aff.setUpdateName("org1.dept2");
        HFCAAffiliationResp resp = aff.update(admin);
        
        assertEquals("Incorrect status code", new Integer(400), new Integer(resp.getStatusCode()));
    }

    // Tests deleting an affiliation
    @Test
    public void testDeleteAffiliation() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        thrown.expectMessage("Affiliation has been deleted");

        HFCAAffiliation aff = client.newHFCAAffiliation("org6");
        aff.create(admin);

        HFCAIdentity ident = client.newHFCAIdentity("testuser_org6");
        ident.setAffiliation("org6");
        ident.create(admin);

        HFCAAffiliation aff2 = client.newHFCAAffiliation("org6.dept1");
        aff2.create(admin);

        HFCAIdentity ident2 = client.newHFCAIdentity("testuser_org6.dept1");
        ident2.setAffiliation("org6.dept1");
        ident2.create(admin);

        HFCAAffiliationResp resp = aff.delete(admin, true);
        int idCount = 0;
        boolean found = false;
        for (HFCAAffiliation childAff : resp.getChildren()) {
            if (childAff.getName().equals("org6.dept1")) {
                found = true;
            }
            for (HFCAIdentity id : childAff.getIdentities()) {
                if (id.getEnrollmentId().equals("testuser_org6.dept1")) {
                    idCount++;
                }
            }
        }

        for (HFCAIdentity id : resp.getIdentities()) {
            if (id.getEnrollmentId().equals("testuser_org6")) {
                idCount++;
            }
        }

        if (!found) {
            fail("Incorrect response received");
        }

        if (idCount != 2) {
            fail("Incorrect number of ids returned");
        }

        assertEquals("Incorrect status code", new Integer(200), new Integer(resp.getStatusCode()));
        assertEquals("Failed to delete affiliation", "org6", aff.getName());

        aff.delete(admin);
    }

    // Tests deleting an affiliation that doesn't require force option
    @Test
    public void testDeleteAffiliationNoForce() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        HFCAAffiliation aff = client.newHFCAAffiliation("org6");
        aff.create(admin);
        HFCAAffiliationResp resp = aff.delete(admin);

        assertEquals("Incorrect status code", new Integer(200), new Integer(resp.getStatusCode()));
        assertEquals("Failed to delete affiliation", "org6", aff.getName());
    }

    // Trying to delete affiliation with child affiliations and identities should result
    // in an error without force option.
    @Test
    public void testForceDeleteAffiliationInvalid() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        thrown.expectMessage("Authorization failure");

        HFCAAffiliation aff = client.newHFCAAffiliation("org1.dept3");
        aff.create(admin);

        HFCAAffiliation aff2 = client.newHFCAAffiliation("org1.dept3.team1");
        aff2.create(admin);

        HFCAIdentity ident = getIdentityReq("testorg1dept3", "client");
        ident.setAffiliation("org1.dept3");
        ident.create(admin);

        HFCAAffiliationResp resp = aff.delete(admin);
        assertEquals("Incorrect status code", new Integer(401), new Integer(resp.getStatusCode()));
    }

    // Tests deleting an affiliation on CA that does not allow affiliation removal
    @Test
    public void testDeleteAffiliationNotAllowed() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            return; // needs v1.1
        }

        thrown.expectMessage("Authorization failure");

        HFCAClient client2 = HFCAClient.createNewInstance(
                config.getOrganization(ORG_NAME_2).getCALocation(),
                config.getOrganization(ORG_NAME_2).getCAProperties());
        client2.setCryptoSuite(crypto);

        // OrganizationUser can be any implementation that implements org.hyperledger.fabric.sdk.User Interface
        OrganizationUser admin2 = store.getMember(ADMIN_NAME, "org2");
        if (!admin2.isEnrolled()) { // Preregistered admin only needs to be enrolled with Fabric CA.
            admin2.setEnrollment(client2.enroll(admin2.getName(), ADMIN_PW));
        }

        HFCAAffiliation aff = client2.newHFCAAffiliation("org6");
        HFCAAffiliationResp resp = aff.delete(admin2);
        assertEquals("Incorrect status code", new Integer(400), new Integer(resp.getStatusCode()));
    }

    // Tests getting server/ca information
    @Test
    public void testGetInfo() throws Exception {

        if (config.isRunningAgainstFabric10()) {
            HFCAInfo info = client.info();
            assertNull(info.getVersion());
        }

        if (!config.isRunningAgainstFabric10()) {
            HFCAInfo info = client.info();
            System.out.println(info);
            assertNotNull("client.info returned null.", info);
            String version = info.getVersion();
            assertNotNull("client.info.getVersion returned null.", version);
            assertTrue(format("Version '%s' didn't match expected pattern", version), version.matches("^\\d+\\.\\d+\\.\\d+($|-.*)"));
        }

    }

    /**
     * 不是有 key 进行认证 
     */
    @Test
    public void testEnrollNoKeyPair() throws Exception {

        thrown.expect(EnrollmentException.class);
        thrown.expectMessage("Failed to enroll user");

        OrganizationUser user = enrolledGeneratorUser(ADMIN_ORG);

        EnrollmentRequest req = new EnrollmentRequest(DEFAULT_PROFILE_NAME, "label 1", null);
        req.setCsr("test");
        
        client.enroll(user.getName(), user.getEnrollmentSecret(), req); // EnrollmentException
    }

    @Test
    public void testRevokeNotAuthorized() throws Exception {

        //thrown.expect(RevocationException.class);
        //thrown.expectMessage("Error while revoking the user");

        // See if a normal user can revoke the admin...
        OrganizationUser user = enrolledGeneratorUser(ADMIN_ORG);
        client.revoke(user, admin.getName(), "revoke admin"); // user 没有权限进行撤销
        //client.revoke(admin, user.getName(), "revoke admin"); // 成功撤销
    }

    @Test
    public void testEnrollSameUser() throws Exception {

        // thrown.expect(RevocationException.class);
        // thrown.expectMessage("does not have attribute 'hf.Revoker'");

        // 看看一个普通用户是否可以撤销管理员...
        OrganizationUser user1 = enrolledGeneratorUser(ADMIN_ORG);

        File sampleStoreFile = new File(System.getProperty("java.io.tmpdir") + "/HFCSampletest.properties");
        if (sampleStoreFile.exists()) { // For testing start fresh
            sampleStoreFile.delete();
        }
        store = new KeyValueFileStore(sampleStoreFile);
        sampleStoreFile.deleteOnExit();

        OrganizationUser user2 = enrolledGeneratorUser(ADMIN_ORG);

        client.revoke(admin, user2.getName(), "revoke admin"); // 普通用户没有权限撤销管理员，撤销操作需要管理员权限
        //client.enroll(user2.getName(), user2.getEnrollmentSecret()); // 被撤销的用户无法进行认证
    }

    // Tests enrolling a user to an unknown CA client
    @Test
    public void testEnrollUnknownClient() throws Exception {

        thrown.expect(EnrollmentException.class);
        thrown.expectMessage("Failed to enroll user");

        CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();

        // This client does not exist
        String clientName = "test CA client";

        HFCAClient clientWithName = HFCAClient.createNewInstance(clientName,
                config.getOrganization(ORG_NAME_1).getCALocation(),
                config.getOrganization(ORG_NAME_1).getCAProperties());
        clientWithName.setCryptoSuite(cryptoSuite);

        clientWithName.enroll(admin.getName(), ADMIN_PW);
    }

    // revoke2: revoke(User revoker, String revokee, String reason)
    @Test
    public void testRevoke2UnknownUser() throws Exception {

        thrown.expect(RevocationException.class);
        thrown.expectMessage("Error while revoking");

        client.revoke(admin, "unknownUser", "remove user2"); //Identity unknownUser was not found
    }

    @Test
    public void testMockEnrollSuccessFalse() throws Exception {

        thrown.expect(EnrollmentException.class);
        thrown.expectMessage("failed enrollment for user");

        MockHFCAClient mockClient = MockHFCAClient.createNewInstance(
                config.getOrganization(ORG_NAME_1).getCALocation(),
                config.getOrganization(ORG_NAME_1).getCAProperties());
        mockClient.setCryptoSuite(crypto);

        OrganizationUser user = enrolledGeneratorUser(ADMIN_ORG);

        mockClient.setHttpPostResponse("{\"success\":false}");
        mockClient.enroll(user.getName(), user.getEnrollmentSecret());
    }

    @Ignore
    @Test
    public void testMockEnrollNoCert() throws Exception {

        thrown.expect(EnrollmentException.class);
        thrown.expectMessage("failed enrollment for user");

        MockHFCAClient mockClient = MockHFCAClient.createNewInstance(
                config.getOrganization(ORG_NAME_1).getCALocation(),
                config.getOrganization(ORG_NAME_1).getCAProperties());
        mockClient.setCryptoSuite(crypto);

        OrganizationUser user = enrolledGeneratorUser(ADMIN_ORG);

        mockClient.setHttpPostResponse("{\"success\":true}");
        mockClient.enroll(user.getName(), user.getEnrollmentSecret());
    }

    @Test
    public void testMockEnrollNoResult() throws Exception {

        thrown.expect(EnrollmentException.class);
        thrown.expectMessage("response did not contain a result");

        MockHFCAClient mockClient = MockHFCAClient.createNewInstance(
                config.getOrganization(ORG_NAME_1).getCALocation(),
                config.getOrganization(ORG_NAME_1).getCAProperties());
        mockClient.setCryptoSuite(crypto);

        OrganizationUser user = enrolledGeneratorUser(ADMIN_ORG);

        mockClient.setHttpPostResponse("{\"success\":true}");
        mockClient.enroll(user.getName(), user.getEnrollmentSecret());
    }

    @Test
    public void testMockEnrollWithMessages() throws Exception {

        MockHFCAClient mockClient = MockHFCAClient.createNewInstance(
                config.getOrganization(ORG_NAME_1).getCALocation(),
                config.getOrganization(ORG_NAME_1).getCAProperties());
        mockClient.setCryptoSuite(crypto);

        OrganizationUser user = enrolledGeneratorUser(ADMIN_ORG);

        mockClient.setHttpPostResponse(
                "{\"success\":true, \"result\":{\"Cert\":\"abc\"}, \"messages\":[{\"code\":123, \"message\":\"test message\"}]}");
        mockClient.enroll(user.getName(), user.getEnrollmentSecret());
    }

    @Test
    public void testMockReenrollNoResult() throws Exception {

        thrown.expect(EnrollmentException.class);
        // thrown.expectMessage("failed");

        MockHFCAClient mockClient = MockHFCAClient.createNewInstance(
                config.getOrganization(ORG_NAME_1).getCALocation(),
                config.getOrganization(ORG_NAME_1).getCAProperties());
        mockClient.setCryptoSuite(crypto);

        OrganizationUser user = enrolledGeneratorUser(ADMIN_ORG);

        mockClient.setHttpPostResponse("{\"success\":true}");
        mockClient.reenroll(user);
    }

    @Ignore
    @Test
    public void testMockReenrollNoCert() throws Exception {

        thrown.expect(EnrollmentException.class);
        thrown.expectMessage("failed re-enrollment for user");

        MockHFCAClient mockClient = MockHFCAClient.createNewInstance(
                config.getOrganization(ORG_NAME_1).getCALocation(),
                config.getOrganization(ORG_NAME_1).getCAProperties());
        mockClient.setCryptoSuite(crypto);

        OrganizationUser user = enrolledGeneratorUser(ADMIN_ORG);

        mockClient.setHttpPostResponse("{\"success\":true}");
        mockClient.reenroll(user);
    }

    // ==========================================================================================
    // Helper methods
    // ==========================================================================================
    private void verifyOptions(String cert, EnrollmentRequest req) throws CertificateException {
        try {
            BufferedInputStream pem = new BufferedInputStream(new ByteArrayInputStream(cert.getBytes()));
            CertificateFactory certFactory = CertificateFactory.getInstance(Config.getConfig().getCertificateFormat());
            X509Certificate certificate = (X509Certificate) certFactory.generateCertificate(pem);

            // check Subject Alternative Names
            Collection<List<?>> altNames = certificate.getSubjectAlternativeNames();
            if (altNames == null) {
                if (req.getHosts() != null && !req.getHosts().isEmpty()) {
                    fail("Host name is not included in certificate");
                }
                return;
            }
            
            ArrayList<String> subAltList = new ArrayList<>();
            for (List<?> item : altNames) {
            	out("SubjectAlternativeName: %s", json(item));
            	
                int type = (Integer) item.get(0);
                if (type == 2) {
                    subAltList.add((String) item.get(1));
                }
            }
            
            if (!subAltList.equals(req.getHosts())) {
                fail("Subject Alternative Names not matched the host names specified in enrollment request");
            }
        } catch (CertificateParsingException e) {
            fail("Cannot parse certificate. Error is: " + e.getMessage());
            throw e;
        } catch (CertificateException e) {
            fail("Cannot regenerate x509 certificate. Error is: " + e.getMessage());
            throw e;
        }
    }

    // Returns a new (unique) user for use in a single test
    private OrganizationUser generatorUser(String org) {
        String userName = userNamePrefix + (++userCount);
        return store.getMember(userName, org);
    }

    // Returns an enrolled user
    private OrganizationUser enrolledGeneratorUser(String org) throws Exception {
        OrganizationUser user = generatorUser(org);
        
        RegistrationRequest rr = new RegistrationRequest(user.getName(), USER_AFFILIATION);
        String password = "password";
        rr.setSecret(password);
        
        user.setEnrollmentSecret(client.register(rr, admin)); // 注册
        
        if (!user.getEnrollmentSecret().equals(password)) {
            fail("Secret returned from RegistrationRequest not match : " + user.getEnrollmentSecret());
        }
        
        user.setEnrollment(client.enroll(user.getName(), user.getEnrollmentSecret())); // 认证
        return user;
    }

    private HFCAIdentity getIdentityReq(String enrollmentID, String type) throws InvalidArgumentException {
        String password = "password";

        HFCAIdentity ident = client.newHFCAIdentity(enrollmentID); // 创建一个新的HFCA 身份对象
        ident.setSecret(password);
        ident.setAffiliation(USER_AFFILIATION);
        ident.setMaxEnrollments(1);
        ident.setType(type);

        Collection<Attribute> attributes = new ArrayList<Attribute>();
        attributes.add(new Attribute("testattr1", "valueattr1"));
        ident.setAttributes(attributes);
        return ident;
    }

    private void sleepALittle() {
        // Seems to be an odd that calling back too quickly can once in a while generate an error on the fabric_ca
        // try {
        // Thread.sleep(5000);
        // } catch (InterruptedException e) {
        // e.printStackTrace();
        // }
    }
    
    private static String json(Object o) {
		if (o != null)
			//return o.toString();
			return new Gson().toJson(o);
		
		return "NULL";
	}

    static void out(String format, Object... args) {
        System.err.flush();
        System.out.flush();
        System.out.println(format(format, args));
        System.err.flush();
        System.out.flush();
    }
}
