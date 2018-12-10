package com.cnblogs.hoojo.fabric.sdk.integration;

import java.nio.file.Paths;

import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.TransactionRequest.Type;
import org.junit.Test;

import com.cnblogs.hoojo.fabric.sdk.entity.InstallEntity;
import com.cnblogs.hoojo.fabric.sdk.model.Organization;

/**
 * Java Chaincode Integration Example
 * @author hoojo
 * @createDate 2018年12月5日 下午6:25:53
 * @file JavaChaincodeIntegrationExamples.java
 * @package com.cnblogs.hoojo.fabric.sdk.integration
 * @project fabric-sdk-integrate
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class JavaChaincodeIntegrationExamples extends GoChaincodeIntegrationExamples {

	String CHAIN_CODE_FILEPATH = "javacc/sample1";
	
	{
		CHAIN_CODE_LANG = Type.JAVA;
		CHAIN_CODE_NAME = "example_cc_java";
		CHAIN_CODE_PATH = null;

		id = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME).setVersion(CHAIN_CODE_VERSION).build();
		id_11 = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME).setVersion(CHAIN_CODE_VERSION_11).build();
	}
	

	@Test
	public void testInstallChaincode() {
		try {
			Channel channel = getChannel();
			Organization org = config.getOrganization(orgName);
			
			InstallEntity chaincode = new InstallEntity(id, CHAIN_CODE_LANG);
			chaincode.setChaincodeSourceFile(Paths.get(config.getCommonConfigRootPath(), CHAIN_CODE_FILEPATH).toFile());
			
			client.setUserContext(org.getPeerAdmin());
			if (chaincodeManager.checkInstallChaincode(channel, chaincode.getChaincodeId())) {
				System.out.println(String.format("通道  %s 已安装chaincode 无需重复安装：%s ", channel.getName(), chaincode.getChaincodeId()));
				return;
			}
			
			chaincodeManager.installChaincode(channel, chaincode);

			if (!chaincodeManager.checkInstallChaincode(channel, chaincode.getChaincodeId())) {
				throw new AssertionError("chaincode 1 没有安装");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testInstantiateChaincode() {
		super.testInstantiateChaincode();
	}
	
	@Test
	public void testQueryChaincode() {
		super.testQueryChaincode();
	}
	
	@Test
	public void testInvokeChaincode() {
		super.testInvokeChaincode();
	}
}
