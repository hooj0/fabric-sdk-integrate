package com.cnblogs.hoojo.fabric.sdk.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.hyperledger.fabric.sdk.helper.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cnblogs.hoojo.fabric.sdk.model.Organization;

/**
 * <b> 默认配置中心</b>
 * 可以覆盖任何其他的配置
 * org.hyperledger.fabric.sdk.configuration
 * @author hoojo
 * @createDate 2018年6月11日 下午5:03:17
 * @file DefaultConfiguration.java
 * @package com.cnblogs.hoojo.fabric.end2end.config
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class DefaultConfiguration {
	
	private final static Logger logger = LoggerFactory.getLogger(DefaultConfiguration.class);

	/** 配置Key前缀 */
	protected static final String PREFIX = "org.hyperledger.fabric.sdk.integration.";
	/** 默认SDK配置 */
	private static final String DEFAULT_SDK_CONFIG = "default-config.properties";
	/** 系统变量 SDK配置 */
	private static final String FABRIC_SDK_CONFIG = "org.hyperledger.fabric.sdk.integration.configuration";
	
	private static final String FABRIC_NETWORK_HOST_KEY = "ORG_HYPERLEDGER_FABRIC_SDK_NETWORK_HOST";
	/** fabric network host 区块链网络的主机IP地址 */
	private static final String FABRIC_NETWORK_HOST = StringUtils.defaultString(System.getenv(FABRIC_NETWORK_HOST_KEY), "192.168.8.8");

	/** 调用合约等待时间 */
	private static final String INVOKE_WAIT_TIME = PREFIX + "InvokeWaitTime";
	/** 部署合约等待时间 */
	private static final String DEPLOY_WAIT_TIME = PREFIX + "DeployWaitTime";
	/** 发起proposal等待时间 */
	private static final String PROPOSAL_WAIT_TIME = PREFIX + "ProposalWaitTime";

	/** 区块链网络配置key的前缀 */
	private static final String FABRIC_NETWORK_KEY_PREFIX = PREFIX + "application.org.";
	/** 匹配到 mspid 值*/
	private static final Pattern ORG_MSPID_PATTERN = Pattern.compile("^" + Pattern.quote(FABRIC_NETWORK_KEY_PREFIX) + "([^\\.]+)\\.mspid$");

	/** tls */
	protected static final String TLS_PATH = PREFIX + "application.tls";
	
	/** 不同版本通道、证书、交易配置 v1.0 and v1.1 src/test/fixture/sdkintegration/e2e-2Orgs */
	private static final String FABRIC_CONFIG_GENERATOR_VERSION = "FABRIC_CONFIG_GENERATOR_VERSION"; //"v1.0" : "v1.1";
	
	/** chaincode 和 组织 、通道、区块配置的根目录 */
	private static final String COMMON_CONFIG_ROOT_PATH = "COMMON_CONFIG_ROOT_PATH_LOCATION";
	/** crypto-config & channel-artifacts 根目录 */
	private static final String CRYPTO_TX_CONFIG_ROOT_PATH = "CRYPTO_TX_CONFIG_ROOT_PATH_LOCATION";
	/** chaincode 源码文件路径 */
	private static final String CHAINCODE_SOURCE_CODE_FILE_PATH = "CHAINCODE_SOURCE_CODE_FILE_PATH_LOCATION";
	/** 通道配置 路径*/
	private static final String CHANNEL_TRANSACTION_FILE_PATH = "CHANNEL_TRANSACTION_FILE_PATH_LOCATION";
	/** chaincode背书策略文件路径 */
	private static final String ENDORSEMENT_POLICY_FILE_PATH = "ENDORSEMENT_POLICY_FILE_PATH_LOCATION";
	/** fabric network  config 配置文件路径 */
	private static final String NETWORK_CONFIG_DIR_FILE_PATH = "NETWORK_CONFIG_DIR_FILE_PATH_LOCATION";
	
	/** SDK 配置 */
	private static final Properties sdkProperties = new Properties();
	/** ORG 配置 */
	private static final HashMap<String, Organization> ORGANIZATION_RESOURCES = new HashMap<>();
	
	private static DefaultConfiguration config;
	
	/** 开启TLS证书，也就是https(grpcs)协议和http(grpc)协议之间的切换 */
	private final boolean runningTLS;
	private final boolean runningFabricCATLS;
	private final boolean runningFabricTLS;

	private DefaultConfiguration() {
		File configFile;
		InputStream stream;

		try {
			// 读取 sdk 配置文件，没有就读取默认配置 DEFAULT_CONFIG
			String configPath = System.getProperty(FABRIC_SDK_CONFIG, DEFAULT_SDK_CONFIG);
			configFile = new File(configPath).getAbsoluteFile();
			logger.info("FileSystem加载SDK配置文件： {}， 配置文件是否存在: {}", configFile.toString(), configFile.exists());
			
			if (!configFile.exists()) {
				stream = DefaultConfiguration.class.getResourceAsStream("/" + configFile.getName());
				logger.info("ClassPath加载SDK配置文件： {}， 配置文件是否存在: {}", configFile.getName(), stream != null);
			} else {
				stream = new FileInputStream(configFile);
			}
			
			sdkProperties.load(stream);
		} catch (Exception e) { // if not there no worries just use defaults
			logger.warn("加载SDK配置文件: {} 失败. 使用SDK默认配置", DEFAULT_SDK_CONFIG);
		} finally {

			configurationDefaultValues();
			
			// TLS 
			String tls = getSDKProperty(TLS_PATH, System.getenv("ORG_HYPERLEDGER_FABRIC_SDKTEST_INTEGRATIONTESTS_TLS"));
			logger.debug("tls: {}", tls);
			runningTLS = StringUtils.equals(tls, "true");
			runningFabricCATLS = runningTLS;
			runningFabricTLS = runningTLS;
			
			// 找到组织配置 peerOrg1/peerOrg2
			addOrganizationResources();

			// 设置组织 orderer、peer、eventhub、domain、cert等配置
			for (Map.Entry<String, Organization> org : ORGANIZATION_RESOURCES.entrySet()) {
				final Organization organization = org.getValue();
				final String orgName = org.getKey();

				final String domainName = getSDKProperty(FABRIC_NETWORK_KEY_PREFIX + orgName + ".domname");
				organization.setDomainName(domainName);

				addPeerLocation(organization, orgName);
				addOrdererLocation(organization, orgName);
				addEventHubLocation(organization, orgName);

				setCAProperties(organization, orgName);
				
				logger.debug("最终organization配置：{}", organization);
			}

			logger.debug("最终ORGANIZATION_RESOURCES配置：{}", ORGANIZATION_RESOURCES);
		}
	}
	
	/** 添加组织事件总线URL配置 */
	private void addEventHubLocation(Organization organization, String orgName) {
		String eventHubProps = getSDKProperty(FABRIC_NETWORK_KEY_PREFIX + orgName + ".eventhub_locations");
		String[] eventHubs = eventHubProps.split("[ \t]*,[ \t]*");
		for (String eventHub : eventHubs) {
			String[] key_val = eventHub.split("[ \t]*@[ \t]*");
			organization.addEventHubLocation(key_val[0], grpcTLSify(key_val[1]));
			
			logger.debug("addEventHubLocation：{}->{}", key_val[0], grpcTLSify(key_val[1]));
		}
	}
	
	/** 添加Orderer服务URL配置 */
	private void addOrdererLocation(Organization organization, String orgName) {
		String ordererProps = getSDKProperty(FABRIC_NETWORK_KEY_PREFIX + orgName + ".orderer_locations");
		String[] orderers = ordererProps.split("[ \t]*,[ \t]*");
		for (String orderer : orderers) {
			String[] key_val = orderer.split("[ \t]*@[ \t]*");
			organization.addOrdererLocation(key_val[0], grpcTLSify(key_val[1]));
			
			logger.debug("addOrdererLocation：{}->{}", key_val[0], grpcTLSify(key_val[1]));
		}
	}
	
	/** 添加Peer节点URL配置 */
	private void addPeerLocation(Organization organization, String orgName) {
		String peerProps = getSDKProperty(FABRIC_NETWORK_KEY_PREFIX + orgName + ".peer_locations");
		String[] peers = peerProps.split("[ \t]*,[ \t]*");
		for (String peer : peers) {
			String[] key_val = peer.split("[ \t]*@[ \t]*");
			organization.addPeerLocation(key_val[0], grpcTLSify(key_val[1]));
			
			logger.debug("addPeerLocation：{}->{}", key_val[0], grpcTLSify(key_val[1]));
		}
	}
	
	/** 设置 CA 配置 */
	private void setCAProperties(Organization organization, String orgName) {
		organization.setCALocation(httpTLSify(getSDKProperty((FABRIC_NETWORK_KEY_PREFIX + orgName + ".ca_location"))));
		organization.setCAName(getSDKProperty((FABRIC_NETWORK_KEY_PREFIX + orgName + ".caName")));
		
		if (runningFabricCATLS) {
			String cert = getCryptoTxConfigRootPath() + "/crypto-config/peerOrganizations/DNAME/ca/ca.DNAME-cert.pem";
			cert = cert.replaceAll("DNAME", organization.getDomainName());
			
			File certFile = new File(cert);
			if (!certFile.exists() || !certFile.isFile()) {
				logger.debug("certFile path: {}", certFile.getAbsolutePath());
				throw new RuntimeException("证书文件不存在： " + certFile.getAbsolutePath());
			}
			
			Properties properties = new Properties();
			properties.setProperty("pemFile", certFile.getAbsolutePath());
			properties.setProperty("allowAllHostNames", "true"); // testing environment only NOT FOR PRODUCTION!

			organization.setCAProperties(properties);
		}
		
		logger.debug("ca properties: {}", organization.getCAProperties());
	}
	
	private void addOrganizationResources() {
		for (Map.Entry<Object, Object> item : sdkProperties.entrySet()) {
			final String key = item.getKey() + "";
			final String val = item.getValue() + "";

			if (key.startsWith(FABRIC_NETWORK_KEY_PREFIX)) {
				
				Matcher match = ORG_MSPID_PATTERN.matcher(key);
				if (match.matches() && match.groupCount() == 1) {
					String orgName = match.group(1).trim();
					
					Organization org = new Organization(orgName, val.trim());
					ORGANIZATION_RESOURCES.put(orgName, org);
					
					logger.debug("添加组织: {} => {}", orgName, org);
				}
			}
		}
	}
	
	private void configurationDefaultValues() {
		// Default values
		defaultProperty(INVOKE_WAIT_TIME, "120");
		defaultProperty(DEPLOY_WAIT_TIME, "120000");
		defaultProperty(PROPOSAL_WAIT_TIME, "120000");

		// Default network values
		defaultProperty(FABRIC_NETWORK_KEY_PREFIX + "peerOrg1.mspid", "Org1MSP");
		defaultProperty(FABRIC_NETWORK_KEY_PREFIX + "peerOrg1.domname", "org1.example.com");
		defaultProperty(FABRIC_NETWORK_KEY_PREFIX + "peerOrg1.caName", "ca0");
		defaultProperty(FABRIC_NETWORK_KEY_PREFIX + "peerOrg1.ca_location", "http://" + FABRIC_NETWORK_HOST + ":7054");
		defaultProperty(FABRIC_NETWORK_KEY_PREFIX + "peerOrg1.orderer_locations", "orderer.example.com@grpc://" + FABRIC_NETWORK_HOST + ":7050");
		defaultProperty(FABRIC_NETWORK_KEY_PREFIX + "peerOrg1.peer_locations", "peer0.org1.example.com@grpc://" + FABRIC_NETWORK_HOST + ":7051, peer1.org1.example.com@grpc://" + FABRIC_NETWORK_HOST + ":7056");
		defaultProperty(FABRIC_NETWORK_KEY_PREFIX + "peerOrg1.eventhub_locations", "peer0.org1.example.com@grpc://" + FABRIC_NETWORK_HOST + ":7053, peer1.org1.example.com@grpc://" + FABRIC_NETWORK_HOST + ":7058");
		
		defaultProperty(FABRIC_NETWORK_KEY_PREFIX + "peerOrg2.mspid", "Org2MSP");
		defaultProperty(FABRIC_NETWORK_KEY_PREFIX + "peerOrg2.domname", "org2.example.com");
		//defaultProperty(FABRIC_NETWORK_KEY_PREFIX + "peerOrg2.caName", "ca1");
		defaultProperty(FABRIC_NETWORK_KEY_PREFIX + "peerOrg2.ca_location", "http://" + FABRIC_NETWORK_HOST + ":8054");
		defaultProperty(FABRIC_NETWORK_KEY_PREFIX + "peerOrg2.orderer_locations", "orderer.example.com@grpc://" + FABRIC_NETWORK_HOST + ":7050");
		defaultProperty(FABRIC_NETWORK_KEY_PREFIX + "peerOrg2.peer_locations", "peer0.org2.example.com@grpc://" + FABRIC_NETWORK_HOST + ":8051, peer1.org2.example.com@grpc://" + FABRIC_NETWORK_HOST + ":8056");
		defaultProperty(FABRIC_NETWORK_KEY_PREFIX + "peerOrg2.eventhub_locations", "peer0.org2.example.com@grpc://" + FABRIC_NETWORK_HOST + ":8053, peer1.org2.example.com@grpc://" + FABRIC_NETWORK_HOST + ":8058");

		// Default tls values
		defaultProperty(TLS_PATH, null);
		
		logger.debug("SDK Properties：{}", sdkProperties);
	}
	
	public boolean isRunningFabricTLS() {
		return runningFabricTLS;
	}

	/** GRPC 协议 开启TLS证书 */
	private String grpcTLSify(String location) {
		location = location.trim();
		Exception e = Utils.checkGrpcUrl(location);
		if (e != null) {
			throw new RuntimeException(String.format("Bad TEST parameters for grpc url %s", location), e);
		}

		return runningFabricTLS ? location.replaceFirst("^grpc://", "grpcs://") : location;
	}

	/** HTTP 协议 开启TLS证书 */
	private String httpTLSify(String location) {
		location = location.trim();

		return runningFabricCATLS ? location.replaceFirst("^http://", "https://") : location;
	}

	/**
	 * 返回单例的SDK配置
	 */
	public static DefaultConfiguration getConfig() {
		if (null == config) {
			config = new DefaultConfiguration();
		}

		return config;
	}
	
	public static DefaultConfiguration resetConfig() {
		config = null;
		
		return getConfig();
	}

	/**
	 * 从 SDK 配置文件中读取配置
	 */
	private String getProperty(String property) {
		String ret = getSDKProperty(property);
		
		if (null == ret) {
			logger.warn("空值配置：{}", property);
		}
		return ret;
	}
	
	private static String getSDKProperty(String property, String defaultValue) {
		return sdkProperties.getProperty(property, defaultValue);
	}
	
	private static String getSDKProperty(String property) {
		return sdkProperties.getProperty(property);
	}

	/**
	 * 默认配置优先读取 系统级别 配置，如果系统环境配置为空，则读取运行变量中的配置
	 */
	private static void defaultProperty(String key, String value) {

		String data = System.getProperty(key);
		logger.trace("读取系统环境配置：{} => {}", key, data);
		
		if (data == null) {
			String envKey = key.toUpperCase().replaceAll("\\.", "_");
			data = System.getenv(envKey);
			
			logger.trace("读取变量环境配置：{} => {}", envKey, data);
			if (data == null) {
				if (null == getSDKProperty(key) && value != null) {
					data = value;
					logger.trace("使用默认配置：{} => {}", key, data);
				}
			}
		}

		if (data != null) {
			sdkProperties.put(key, data);
			logger.debug("添加SDK配置: {} => {}", key, data);
		}
	}

	/** 交易等待时间 */
	public int getTransactionWaitTime() {
		return Integer.parseInt(getProperty(INVOKE_WAIT_TIME));
	}

	/** 部署等待时间 */
	public int getDeployWaitTime() {
		return Integer.parseInt(getProperty(DEPLOY_WAIT_TIME));
	}

	/** 交易动作等待时间 */
	public long getProposalWaitTime() {
		return Integer.parseInt(getProperty(PROPOSAL_WAIT_TIME));
	}

	/** 节点配置 */
	public Properties getPeerProperties(String name) {
		Properties props = getTLSCertProperties("peer", name);
		
		logger.debug("{} properties: {}", name, props);
		return props;
	}

	/** orderer 服务配置 */
	public Properties getOrdererProperties(String name) {
		Properties props = getTLSCertProperties("orderer", name);
		
		logger.debug("{} properties: {}", name, props);
		return props;
	}
	
	/** 事件机制配置 */
	public Properties getEventHubProperties(String name) {
		Properties props = getTLSCertProperties("peer", name); // uses same as named peer
		
		logger.debug("{} properties: {}", name, props);
		return props;
	}
	
	private String getDomainName(final String name) {
		int dot = name.indexOf(".");
		if (-1 == dot) {
			return null;
		} else {
			return name.substring(dot + 1);
		}
	}

	public Properties getTLSCertProperties(final String type, final String name) {
		Properties props = new Properties();

		final String domainName = getDomainName(name);

		File cert = Paths.get(getCryptoTxConfigRootPath(), "crypto-config/ordererOrganizations".replace("orderer", type), domainName, type + "s", name, "tls/server.crt").toFile();
		if (!cert.exists()) {
			throw new RuntimeException(String.format("Missing cert file for: %s. Could not find at location: %s", name, cert.getAbsolutePath()));
		}

		if (!isRunningAgainstFabric10()) {
			File clientCert;
			File clientKey;
			if ("orderer".equals(type)) {
				clientCert = Paths.get(getCryptoTxConfigRootPath(), "crypto-config/ordererOrganizations/example.com/users/Admin@example.com/tls/client.crt").toFile();
				clientKey = Paths.get(getCryptoTxConfigRootPath(), "crypto-config/ordererOrganizations/example.com/users/Admin@example.com/tls/client.key").toFile();
			} else {
				clientCert = Paths.get(getCryptoTxConfigRootPath(), "crypto-config/peerOrganizations/", domainName, "users/User1@" + domainName, "tls/client.crt").toFile();
				clientKey = Paths.get(getCryptoTxConfigRootPath(), "crypto-config/peerOrganizations/", domainName, "users/User1@" + domainName, "tls/client.key").toFile();
			}

			if (!clientCert.exists()) {
				throw new RuntimeException(String.format("Missing  client cert file for: %s. Could not find at location: %s", name, clientCert.getAbsolutePath()));
			}

			if (!clientKey.exists()) {
				throw new RuntimeException(String.format("Missing  client key file for: %s. Could not find at location: %s", name, clientKey.getAbsolutePath()));
			}
			
			props.setProperty("clientCertFile", clientCert.getAbsolutePath());
			props.setProperty("clientKeyFile", clientKey.getAbsolutePath());
		}

		props.setProperty("pemFile", cert.getAbsolutePath());
		props.setProperty("hostnameOverride", name);
		props.setProperty("sslProvider", "openSSL");
		props.setProperty("negotiationType", "TLS");
		
		return props;
	}

	/** configtxlator 配置转换工具URL配置 */
	public String getFabricConfigTxLaterURL() {
		return "http://" + FABRIC_NETWORK_HOST + ":7059";
	}

	public boolean isRunningAgainstFabric10() {
		return getFabricConfigGeneratorVersion().contains("1.0");
	}

	/** 获取全部组织 */
	public Collection<Organization> getOrganizations() {
		return Collections.unmodifiableCollection(ORGANIZATION_RESOURCES.values());
	}

	/** 获取组织 */
	public Organization getOrganization(String name) {
		return ORGANIZATION_RESOURCES.get(name);
	}
	
	public String getFabricConfigGeneratorVersion() {
		return getSDKProperty(FABRIC_CONFIG_GENERATOR_VERSION, System.getenv("FAB_CONFIG_GEN_VERS"));
	}
	
	/** crypto-config & channel-artifacts 根目录 */
	public String getCryptoTxConfigRootPath() {
		return Paths.get(getCommonConfigRootPath(), getSDKProperty(CRYPTO_TX_CONFIG_ROOT_PATH, "/e2e-2Orgs/"), getFabricConfigGeneratorVersion()).toString();
	}
	
	/** 通道配置路径 */
	public String getChannelPath() {
		return Paths.get(getCryptoTxConfigRootPath(), getSDKProperty(CHANNEL_TRANSACTION_FILE_PATH, "/channel-artifacts")).toString();
	}
	
	/** 通道、区块、组织等配置根目录 */
	public String getCommonConfigRootPath() {
		return getSDKProperty(COMMON_CONFIG_ROOT_PATH); // src/test/fixture/sdkintegration
	}
	
	/** 通道tx配置目录 */
	public String getChaincodePath() {
		return Paths.get(getCommonConfigRootPath(), getSDKProperty(CHAINCODE_SOURCE_CODE_FILE_PATH)).toString();
	}
	
	/** 背书文件配置路径 */
	public String getEndorsementPolicyFilePath() {
		return Paths.get(getCommonConfigRootPath(), getSDKProperty(ENDORSEMENT_POLICY_FILE_PATH)).toString();
	}

	/** network config 父目录配置路径 */
	public String getNetworkConfigDirFilePath() {
		return Paths.get(getCommonConfigRootPath(), getSDKProperty(NETWORK_CONFIG_DIR_FILE_PATH)).toString();
	}

	/** 
	 * 如果host不是localhost，将替换 network-config.yaml 中的host地址
	 */
	public File getNetworkConfigFile() {
		String fileName = runningTLS ? "network-config-tls.yaml" : "network-config.yaml";
		String filePath = getNetworkConfigDirFilePath(); //"src/test/fixture/sdkintegration/network_configs/";
		
		File networkConfig = new File(filePath, fileName);
		logger.trace("network yaml 文件最终位置：{}", networkConfig);
		if (!"localhost".equals(FABRIC_NETWORK_HOST)) {
			// change on the fly ...
			File transferNetworkConfig = null;

			try {
				// create a temp file
				transferNetworkConfig = File.createTempFile(fileName, "-FixedUp.yaml");

				if (transferNetworkConfig.exists()) { // For testing start fresh
					transferNetworkConfig.delete();
				}

				// 读取内容
				byte[] data = Files.readAllBytes(Paths.get(networkConfig.getAbsolutePath()));

				String sourceText = new String(data, StandardCharsets.UTF_8);

				// 替换配置
				sourceText = sourceText.replaceAll("https://localhost", "https://" + FABRIC_NETWORK_HOST);
				sourceText = sourceText.replaceAll("http://localhost", "http://" + FABRIC_NETWORK_HOST);
				sourceText = sourceText.replaceAll("grpcs://localhost", "grpcs://" + FABRIC_NETWORK_HOST);
				sourceText = sourceText.replaceAll("grpc://localhost", "grpc://" + FABRIC_NETWORK_HOST);

				// 写入替换后的内容
				Files.write(Paths.get(transferNetworkConfig.getAbsolutePath()), sourceText.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

				if (!Objects.equals("true", System.getenv(FABRIC_NETWORK_HOST_KEY + "_KEEP"))) {
					transferNetworkConfig.deleteOnExit();
				} else {
					logger.info("network-config.yaml 转换Host后配置文件: {}", transferNetworkConfig.getAbsolutePath());
				}

			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			networkConfig = transferNetworkConfig;
		}

		return networkConfig;
	}
}
