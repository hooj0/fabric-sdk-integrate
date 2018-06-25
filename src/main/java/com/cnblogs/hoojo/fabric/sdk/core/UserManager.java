package com.cnblogs.hoojo.fabric.sdk.core;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.EnrollmentRequest;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.HFCAInfo;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.hyperledger.fabric_ca.sdk.exception.EnrollmentException;
import org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException;

import com.cnblogs.hoojo.fabric.sdk.config.DefaultConfiguration;
import com.cnblogs.hoojo.fabric.sdk.log.ApplicationLogging;
import com.cnblogs.hoojo.fabric.sdk.model.Organization;
import com.cnblogs.hoojo.fabric.sdk.model.OrganizationUser;
import com.cnblogs.hoojo.fabric.sdk.persistence.KeyValueFileStore;
import com.cnblogs.hoojo.fabric.sdk.util.Util;

/**
 * User 管理服务
 * @author hoojo
 * @createDate 2018年6月22日 下午5:23:50
 * @file UserManager.java
 * @package com.cnblogs.hoojo.fabric.sdk.core
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class UserManager extends ApplicationLogging {

	private KeyValueFileStore store;
	private DefaultConfiguration config;
	private Collection<Organization> organizations;

	public UserManager(DefaultConfiguration config, KeyValueFileStore store) {
		this.config = config;
		this.store = store;

		organizations = config.getOrganizations();
	}

	public void initialize(String adminName, String adminSecret, String userName) throws Exception {
		logger.info("init organization user");

		initializeCaClient();
		enrollOrganizationUsers(adminName, adminSecret, userName);
	}

	/**
	 * 启动前 设置每个 org资源的 ca 客户端对象
	 * @author hoojo
	 * @createDate 2018年6月12日 下午5:44:48
	 * @throws MalformedURLException
	 * @throws InvalidArgumentException
	 */
	public void initializeCaClient() throws Exception {
		logger.info("初始化 CA 客户端……");

		// 创建 CA client
		for (Organization org : organizations) {
			String caName = org.getCAName(); // Try one of each name and no name.

			if (org.getCAClient() != null) {
				continue;
			}

			if (StringUtils.isNotBlank(caName)) {
				org.setCAClient(HFCAClient.createNewInstance(caName, org.getCALocation(), org.getCAProperties()));
			} else {
				org.setCAClient(HFCAClient.createNewInstance(org.getCALocation(), org.getCAProperties()));
			}
		}
	}

	/**
	 * CA Admin、User、Peer Admin、TLS 注册和认证
	 * @author hoojo
	 * @createDate 2018年6月13日 上午11:01:15
	 * @throws Exception
	 */
	private void enrollOrganizationUsers(String adminName, String adminSecret, String userName) throws Exception {
		logger.info("Start -> Enroll Organization: CA Admin、User、Peer Admin、TLS ");

		for (Organization org : organizations) {
			logger.info("orgName: {} / mspID: {} 进行用户注册和认证", org.getName(), org.getMSPID());

			HFCAClient ca = org.getCAClient();
			// PKI密钥创建/签署/验证
			ca.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

			/** TLS 证书模式：如何从Fabric CA获取客户端TLS证书，为orderer、peer 使用一个客户端TLS证书 */
			if (config.isRunningFabricTLS()) {
				enrollAdminTLS(org, adminName, adminSecret);
			} else {
				logger.debug("TLS 证书模式：{}", config.isRunningFabricTLS());
			}

			HFCAInfo info = ca.info();
			logger.debug("ca info: {}", info);

			checkNotNull(info, "HFCAInfo is null");
			if (!StringUtils.isBlank(info.getCAName())) {
				checkArgument(StringUtils.equals(info.getCAName(), ca.getCAName()), "HFCAInfo.CAName 和  CaInfo.CAName 不等");
			}

			// admin enroll
			OrganizationUser admin = enrollAdmin(org, adminName, adminSecret);

			// 设置当前组织 admin
			org.setAdmin(admin);

			// user register/enroll
			OrganizationUser user = registerAndEnrollUser(org, userName);

			// 设置当前组织 user
			org.addUser(user);

			// peer admin
			OrganizationUser peerAdmin = wrapperPeerAdmin(org);

			// 设置当前组织 peerAdmin
			org.setPeerAdmin(peerAdmin);
		}
	}

	/**
	 * 从Fabric CA获取客户端TLS证书，使用客户端TLS证书
	 * @author hoojo
	 * @createDate 2018年6月13日 上午10:38:55
	 */
	public Enrollment enrollAdminTLS(Organization org, String adminName, String adminSecret) throws EnrollmentException, InvalidArgumentException, IOException {
		logger.info("管理员 TLS 模式——认证……");

		HFCAClient ca = org.getCAClient();

		// 构建认证请求
		final EnrollmentRequest request = new EnrollmentRequest();
		request.addHost("localhost");
		request.setProfile("tls");
		logger.trace("ca admin request: {}", request);

		// 发起请求进行认证
		final Enrollment enrollment = ca.enroll(adminName, adminSecret, request); // 设置 ca 管理员名称和密码，对应
																					// docker-compose.yaml
		final String tlsCertPEM = enrollment.getCert();
		final String tlsKeyPEM = getPEMString(enrollment.getKey());

		logger.trace("enrollment: {}", enrollment);
		logger.trace("tlsKeyPEM: {}, tlsCertPEM: {}", tlsKeyPEM, tlsCertPEM);

		// 保存证书 key、cert
		store.storeClientPEMTLSCertificate(org, tlsCertPEM);
		store.storeClientPEMTLSKey(org, tlsKeyPEM);

		return enrollment;
	}

	/**
	 * ca Admin 角色 - 管理员认证
	 * 
	 * @author hoojo
	 * @createDate 2018年6月13日 上午10:41:54
	 */
	public OrganizationUser enrollAdmin(Organization org, String adminName, String adminSecret) throws EnrollmentException, InvalidArgumentException {
		logger.info("管理员CA——认证……");

		HFCAClient ca = org.getCAClient();

		// 从缓存或store中获取用户
		OrganizationUser admin = store.getMember(adminName, org.getName());
		if (!admin.isEnrolled()) { // 未认证，只需用用ca client进行认证

			// 认证：获取用户的签名证书和私钥。
			Enrollment enrollment = ca.enroll(admin.getName(), adminSecret);
			logger.trace("用户：{} 进行认证: {}", admin.getName(), enrollment);

			admin.setEnrollmentSecret(adminSecret);
			admin.setEnrollment(enrollment);
			admin.setMspId(org.getMSPID());
		}

		return admin;
	}

	/**
	 * User 角色-普通用户注册和认证
	 * @author hoojo
	 * @createDate 2018年6月13日 上午10:48:39
	 */
	public OrganizationUser registerAndEnrollUser(Organization org, String userName) throws Exception {
		logger.info("普通用户——注册和认证……");

		HFCAClient ca = org.getCAClient();

		// 从缓存或store中获取用户
		OrganizationUser user = store.getMember(userName, org.getName());
		if (!user.isRegistered()) { // 未注册
			// 用户注册
			final RegistrationRequest request = new RegistrationRequest(user.getName(), "org1.department1");

			// 利用管理员权限进行普通user注册
			String secret = ca.register(request, org.getAdmin());
			logger.trace("用户 {} 注册，秘钥：{}", user, secret);

			user.setAffiliation(request.getAffiliation());
			user.setEnrollmentSecret(secret);
		}

		if (!user.isEnrolled()) { // 未认证
			// 用户认证
			Enrollment enrollment = ca.enroll(user.getName(), user.getEnrollmentSecret());
			logger.trace("用户：{} 进行认证: {}", user.getName(), enrollment);

			user.setEnrollment(enrollment);
			user.setMspId(org.getMSPID());
		}

		return user;
	}

	/**
	 * peer节点管理员证书私钥封装
	 * 
	 * @author hoojo
	 * @createDate 2018年6月13日 上午10:53:35
	 * @throws IOException
	 */
	public OrganizationUser wrapperPeerAdmin(Organization org) throws Exception {
		logger.info("节点管理员——认证……");

		final String orgName = org.getName();
		final String mspid = org.getMSPID();
		final String domain = org.getDomainName();

		// src/test/fixture/sdkintegration/e2e-2Orgs/channel/crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/keystore/
		File keydir = Paths.get(config.getChannelPath(), "crypto-config/peerOrganizations/", domain, format("/users/Admin@%s/msp/keystore", domain)).toFile();
		File privateKeyFile = Util.findFileSk(keydir);
		File certificateFile = Paths.get(config.getChannelPath(), "crypto-config/peerOrganizations/", domain, format("/users/Admin@%s/msp/signcerts/Admin@%s-cert.pem", domain, domain)).toFile();

		logger.trace("privateKeyDir: {}", keydir.getAbsolutePath());
		logger.trace("privateKeyFile: {}", privateKeyFile.getAbsolutePath());
		logger.trace("certificateFile: {}", certificateFile.getAbsolutePath());

		// 从缓存或store中获取用户
		OrganizationUser peerAdmin = store.getMember(orgName + "Admin", orgName, mspid, privateKeyFile, certificateFile);
		logger.trace("构建Peer Admin用户：{}", peerAdmin);

		return peerAdmin;
	}

	public String getPEMString(PrivateKey privateKey) throws IOException {
		StringWriter stringWriter = new StringWriter();

		JcaPEMWriter writer = new JcaPEMWriter(stringWriter);
		writer.writeObject(privateKey);
		writer.close();

		return writer.toString();
	}
}
