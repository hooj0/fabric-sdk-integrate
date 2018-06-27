package com.cnblogs.hoojo.fabric.sdk.persistence;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cnblogs.hoojo.fabric.sdk.common.AbstractFabricObject;
import com.cnblogs.hoojo.fabric.sdk.model.Organization;
import com.cnblogs.hoojo.fabric.sdk.model.OrganizationUser;

/**
 * 本地文件键值存储系统 ，做数据持久化存储。可以用redis、db等其他方式实现
 * @author hoojo
 * @createDate 2018年6月12日 下午4:21:41
 * @file KeyValueFileStore.java
 * @package com.cnblogs.hoojo.fabric.sdk.config
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class KeyValueFileStore extends AbstractFabricObject {

	private final static Logger logger = LoggerFactory.getLogger(KeyValueFileStore.class);
	
	private final Map<String, OrganizationUser> members = new HashMap<String, OrganizationUser>();

	private String storeFilePath;
	
	static {
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
	}

	public KeyValueFileStore(File file) {
		this.storeFilePath = file.getAbsolutePath();
		
		logger.debug("持久化KEY-VALUE存储系统文件：{}", this.storeFilePath);
	}

	public String get(String name) {
		Properties properties = loadProperties();
		
		return properties.getProperty(name);
	}

	public void set(String name, String value) {
		Properties properties = loadProperties();
		
		try (OutputStream output = new FileOutputStream(storeFilePath)) {
			properties.setProperty(name, value);
			properties.store(output, "");
			output.close();
		} catch (IOException e) {
			logger.warn("Could not save the keyvalue store, reason: {}", e.getMessage());
		}
	}
	
	public boolean contains(String name) {
		Properties properties = loadProperties();
		
		return properties.containsKey(name);
	}

	private Properties loadProperties() {
		Properties properties = new Properties();
		
		try (InputStream input = new FileInputStream(storeFilePath)) {
			properties.load(input);
			input.close();
		} catch (FileNotFoundException e) {
			logger.warn("Could not find the file \"{}\"", storeFilePath);
		} catch (IOException e) {
			logger.warn("Could not load keyvalue store from file \"{}\", reason:{}", storeFilePath, e.getMessage());
		}

		return properties;
	}
	
	public void cacheMember(String memberStoreKey, OrganizationUser user) {
		// 内存缓存
		members.put(memberStoreKey, user);
	}

	/**
	 * 获取 用户 ，如果不存在就创建，存在就从缓存中获取
	 * @author hoojo
	 * @createDate 2018年6月13日 上午11:21:12
	 * @param name OrganizationUser Name
	 * @param org Organization Name
	 * @return OrganizationUser
	 */
	public OrganizationUser getMember(String name, String org) {
		String memberStoreKey = OrganizationUser.toStoreKey(name, org);
		OrganizationUser user = members.get(memberStoreKey);
		if (null != user) {
			logger.debug("从缓存获取Member：{}", user);
			return user;
		}
		
		user = new OrganizationUser(name, org, this);
		cacheMember(memberStoreKey, user);
		
		return user;
	}

	/**
	 * 
	 * 判断KV文件缓存中是否存在用户
	 * @author hoojo
	 * @createDate 2018年6月13日 上午11:33:25
	 * @param name OrganizationUser 用户名
	 * @param org Organization 组织名称
	 * @return boolean
	 */
	public boolean hasMember(String name, String org) {
		if (members.containsKey(OrganizationUser.toStoreKey(name, org))) {
			return true;
		}
		
		return OrganizationUser.isStored(name, org, this);
	}

	/**
	 * 从缓存获取 OrganizationUser ，如果缓存没有就构建 OrganizationUser
	 * @author hoojo
	 * @createDate 2018年6月13日 上午11:29:49
	 * @param name OrganizationUser Name
	 * @param org Organization Name
	 * @param mspId Organization mspId
	 * @param privateKeyFile 私钥
	 * @param certificateFile 证书
	 * @return OrganizationUser
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws InvalidKeySpecException
	 */
	public OrganizationUser getMember(String name, String org, String mspId, File privateKeyFile, File certificateFile)throws IOException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {

		try {
			String memberStoreKey = OrganizationUser.toStoreKey(name, org);
			OrganizationUser user = members.get(memberStoreKey);
			if (null != user) {
				return user;
			}

			user = new OrganizationUser(name, org, this);

			String certificate = new String(IOUtils.toByteArray(new FileInputStream(certificateFile)), "UTF-8");
			PrivateKey privateKey = getPrivateKeyFromBytes(IOUtils.toByteArray(new FileInputStream(privateKeyFile)));

			user.setMspId(mspId);
			// 登记认证
			user.setEnrollment(new StoreEnrollement(privateKey, certificate));
			// 持久化存储
			user.storeState();
			
			// 内存缓存
			cacheMember(memberStoreKey, user);
			
			return user;
		} catch (IOException e) {
			throw e;
		} catch (NoSuchAlgorithmException e) {
			throw e;
		} catch (NoSuchProviderException e) {
			throw e;
		} catch (InvalidKeySpecException e) {
			throw e;
		} catch (ClassCastException e) {
			throw e;
		}
	}

	/**
	 * 将byte字节私钥转换成 PrivateKey 对象
	 * @author hoojo
	 * @createDate 2018年6月13日 上午11:28:54
	 * @param data key
	 * @return PrivateKey
	 * @throws IOException
	 * @throws NoSuchProviderException
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeySpecException
	 */
	private static PrivateKey getPrivateKeyFromBytes(byte[] data) throws IOException, NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException {
		final Reader pemReader = new StringReader(new String(data));

		final PrivateKeyInfo pemPair;
		try (PEMParser pemParser = new PEMParser(pemReader)) {
			pemPair = (PrivateKeyInfo) pemParser.readObject();
		}

		PrivateKey privateKey = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getPrivateKey(pemPair);

		return privateKey;
	}

	private static final class StoreEnrollement implements Enrollment, Serializable {

		private static final long serialVersionUID = -2784835212445309006L;
		private final PrivateKey privateKey;
		private final String certificate;

		public StoreEnrollement(PrivateKey privateKey, String certificate) {
			this.certificate = certificate;
			this.privateKey = privateKey;
		}

		@Override
		public PrivateKey getKey() {
			return privateKey;
		}

		@Override
		public String getCert() {
			return certificate;
		}
	}
	
	/**
	 * 保存通道到缓存
	 * @author hoojo
	 * @createDate 2018年6月13日 上午11:23:51
	 * @param channel Channel
	 * @throws IOException
	 * @throws InvalidArgumentException
	 */
	public void saveChannel(Channel channel) throws IOException, InvalidArgumentException {
		logger.debug("保存通道到缓存: {}", channel.getName());;
		set("channel." + channel.getName(), Hex.toHexString(channel.serializeChannel()));
	}

	/**
	 * 从缓存获取通道，如果不存在就构建通道对象
	 * @author hoojo
	 * @createDate 2018年6月13日 上午11:24:13
	 * @param client HFClient
	 * @param name 通道名称
	 * @return Channel
	 * @throws IOException
	 * @throws ClassNotFoundException
	 * @throws InvalidArgumentException
	 */
	public Channel getChannel(HFClient client, String name) throws IOException, ClassNotFoundException, InvalidArgumentException {
		Channel channel = null;

		String channelHex = get("channel." + name);
		if (channelHex != null) {
			channel = client.deSerializeChannel(Hex.decode(channelHex));
			logger.debug("恢复通道：{}， channel:{}", name, channel);
		} else {
			logger.debug("没有恢复通道：{}", name);
		}
		
		return channel;
	}

	/**
	 * 保存客户端 证书 PEM tls key
	 * @author hoojo
	 * @createDate 2018年6月13日 上午11:25:13
	 * @param organization 组织
	 * @param key key
	 */
	public void storeClientPEMTLSKey(Organization organization, String key) {
		set("clientPEMTLSKey." + organization.getName(), key);
	}

	/**
	 * 保存客户端 证书 PEM tls key
	 * @author hoojo
	 * @createDate 2018年6月13日 上午11:25:13
	 * @param organization 组织
	 */
	public String getClientPEMTLSKey(Organization organization) {
		return get("clientPEMTLSKey." + organization.getName());
	}

	/**
	 * 保存客户端证书   PEM tls cert 
	 * @author hoojo
	 * @createDate 2018年6月13日 上午11:26:09
	 * @param organization Organization
	 * @param certificate cert
	 */
	public void storeClientPEMTLSCertificate(Organization organization, String certificate) {
		set("clientPEMTLSCertificate." + organization.getName(), certificate);
	}

	/**
	 * 保存客户端证书   PEM tls cert 
	 * @author hoojo
	 * @createDate 2018年6月13日 上午11:26:09
	 * @param organization Organization
	 */
	public String getClientPEMTLSCertificate(Organization organization) {
		return get("clientPEMTLSCertificate." + organization.getName());
	}
}