package com.cnblogs.hoojo.fabric.sdk.config;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * <b>function:</b> configuration test
 * @author hoojo
 * @createDate 2018年6月12日 上午10:14:15
 * @file DefaultConfigurationTest.java
 * @package com.cnblogs.hoojo.fabric.sdk.config
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class DefaultConfigurationTest {

	
	@Test
	public void testDefaultConfig() {
		System.out.println(DefaultConfiguration.getConfig());
	}
	
	@Test
	public void testBasicConfig() {
		DefaultConfiguration config = DefaultConfiguration.getConfig();
		
		assertEquals("src/test/fixture/sdkintegration", config.getCommonConfigRootPath());
		assertEquals("v1.0", config.getFabricConfigGeneratorVersion());
		assertEquals("src/test/fixture/sdkintegration/e2e-2Orgs/v1.0".replaceAll("/", "\\\\"), config.getCryptoTxConfigRootPath());
		assertEquals("src/test/fixture/sdkintegration/gocc/sample1".replaceAll("/", "\\\\"), config.getChaincodePath());
		//assertEquals("src/test/fixture/sdkintegration/e2e-2Orgs/v1.0/channel-artifacts".replaceAll("/", "\\\\"), config.getChannelPath());
		assertEquals("src/test/fixture/sdkintegration/chaincodeendorsementpolicy.yaml".replaceAll("/", "\\\\"), config.getEndorsementPolicyFilePath());
		assertEquals("src/test/fixture/sdkintegration/network_configs".replaceAll("/", "\\\\"), config.getNetworkConfigDirFilePath());
		
	}
}
