package com.cnblogs.hoojo.fabric.sdk.privatedata;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.ChaincodeCollectionConfiguration;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.CollectionConfigPackage;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.BlockEvent.TransactionEvent;
import org.hyperledger.fabric.sdk.TransactionRequest.Type;
import org.hyperledger.fabric.sdk.exception.TransactionEventException;
import org.junit.Test;

import com.cnblogs.hoojo.fabric.sdk.entity.InstallEntity;
import com.cnblogs.hoojo.fabric.sdk.entity.InstantiateUpgradeEntity;
import com.cnblogs.hoojo.fabric.sdk.entity.SendTransactionEntity;
import com.cnblogs.hoojo.fabric.sdk.entity.TransactionEntity;
import com.cnblogs.hoojo.fabric.sdk.integration.GoChaincodeIntegrationExamples;
import com.cnblogs.hoojo.fabric.sdk.model.Organization;
import com.google.common.collect.Maps;

/**
 * private data collection use JavaSDK example
 * 使用私有数据示例
 * @author hoojo
 * @createDate 2018年12月6日 下午3:36:01
 * @file PrivateDataExample.java
 * @package com.cnblogs.hoojo.fabric.sdk.privatedata
 * @project fabric-sdk-integrate
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class PrivateDataExample extends GoChaincodeIntegrationExamples {

	private String CHAIN_CODE_LOCATION = "sdkintegration/gocc/samplePrivateData";

	{
		CHAIN_CODE_LANG = Type.GO_LANG;
	    CHAIN_CODE_NAME = "private_data_cc1_go";
	    CHAIN_CODE_PATH = "github.com/private_data_cc";
	    
	    id = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME).setPath(CHAIN_CODE_PATH).setVersion(CHAIN_CODE_VERSION).build();
	}
	

	@Test
	public void testInstallChaincode() {
		try {
			Channel channel = getChannel();
			Organization org = config.getOrganization(orgName);
			
			InstallEntity chaincode = new InstallEntity(id, CHAIN_CODE_LANG);
			chaincode.setChaincodeSourceFile(Paths.get(config.getCommonConfigRootPath(), CHAIN_CODE_LOCATION).toFile());
			
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
		try {
			Channel channel = getChannel();
			Organization org = config.getOrganization(orgName);
			
			InstantiateUpgradeEntity chaincode = new InstantiateUpgradeEntity(id, CHAIN_CODE_LANG);
			chaincode.setEndorsementPolicy(chaincodeManager.getChaincodeEndorsementPolicy());
			chaincode.setFunc("init");
			chaincode.setArgs(new String[] {});
			chaincode.setSpecificPeers(true);
			
			File file = Paths.get(config.getCommonConfigRootPath(), "collectionProperties/PrivateDataIT.yaml").toFile();
			ChaincodeCollectionConfiguration collectionConfiguration = ChaincodeCollectionConfiguration.fromYamlFile(file);
			chaincode.setCollectionConfiguration(collectionConfiguration);
			
			if (!chaincodeManager.checkInstallChaincode(channel, chaincode.getChaincodeId())) {
				throw new AssertionError("chaincode 1 没有安装");
			} 
			if (chaincodeManager.checkInstantiatedChaincode(channel, chaincode.getChaincodeId())) {
				throw new AssertionError("chaincode 1 已经实例化");
			}
			
			client.setUserContext(org.getPeerAdmin());
			Collection<ProposalResponse> responses = chaincodeManager.instantiateChaincode(channel, chaincode);
			
			SendTransactionEntity transaction = new SendTransactionEntity();
			transaction.setUser(org.getPeerAdmin());
			
			CompletableFuture<TransactionEvent> future = chaincodeManager.sendTransaction(responses, transaction, channel);
			Object result = future.thenApply((BlockEvent.TransactionEvent transactionEvent) -> {
				
				// 必须是有效交易事件
				checkArgument(transactionEvent.isValid(), "没有签名的交易事件");
				// 必须有签名
				checkNotNull(transactionEvent.getSignature(), "没有签名的交易事件");
				// 必须有交易区块事件发生
				BlockEvent blockEvent = checkNotNull(transactionEvent.getBlockEvent(), "交易事件的区块事件对象为空");
				
				try {
					System.out.println("成功实例化Chaincode，本次实例化交易ID：" +  transactionEvent.getTransactionID());
					checkArgument(StringUtils.equals(blockEvent.getChannelId(), channel.getName()), "事件名称和对应通道名称不一致");

					// 检查
					if (!chaincodeManager.checkInstantiatedChaincode(channel, chaincode.getChaincodeId())) {
						throw new AssertionError("chaincode 1 没有实例化");
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				
				return "success";
			}).exceptionally(e -> {
				if (e instanceof CompletionException && e.getCause() != null) {
					e = e.getCause();
				}
				
				if (e instanceof TransactionEventException) {
					BlockEvent.TransactionEvent te = ((TransactionEventException) e).getTransactionEvent();
					if (te != null) {
						e.printStackTrace(System.err);
						fail(format("Transaction with txid %s failed. %s", te.getTransactionID(), e.getMessage()));
					}
				}
				
				e.printStackTrace(System.err);
				fail(format("Test failed with %s exception %s", e.getClass().getName(), e.getMessage()));
				
				return null;
			}).get(config.getTransactionWaitTime(), TimeUnit.SECONDS); 
			
			System.out.println("返回结果：" + result);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testQueryChaincode() {
		try {
			Channel channel = getChannel();
			Organization org = config.getOrganization(orgName);
			
			client.setUserContext(org.getUser(USER_NAME));

			TransactionEntity transaction = new TransactionEntity(id, CHAIN_CODE_LANG);
			transaction.setFunc("query");
			
			Map<String, byte[]> tmap = new HashMap<>();
            tmap.put("B", "b".getBytes(UTF_8)); // test using bytes as args. End2end uses Strings.
            transaction.setTransientMap(tmap);
			
			String account = transactionManager.queryChaincode(channel, transaction);
			System.out.println("account a: " + account);
			

			transaction.setArgs(new String[] { "b" });
			transaction.setFunc("query");
			
			account = transactionManager.queryChaincode(channel, transaction);
			System.out.println("account b: " + account);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testInvokeAmount() {
		String moveAmount = "5";

		try {
			Channel channel = getChannel();
			Organization org = config.getOrganization(orgName);
			
			TransactionEntity transaction = new TransactionEntity(id, CHAIN_CODE_LANG);
			transaction.setFunc("move");
			
			Map<String, byte[]> transientMap = Maps.newHashMap();
			transientMap.put("A", "a".getBytes(UTF_8)); //test using bytes .. end2end uses Strings.
            transientMap.put("B", "b".getBytes(UTF_8));
			transientMap.put("moveAmount", moveAmount.getBytes(UTF_8));
			
			transaction.setTransientMap(transientMap);
			
			client.setUserContext(org.getUser(USER_NAME));
			
			Collection<ProposalResponse> responses = transactionManager.invokeChaincode(channel, transaction);
			
			SendTransactionEntity sendTransaction = new SendTransactionEntity();
			sendTransaction.setUser(org.getUser(USER_NAME));
			
			CompletableFuture<TransactionEvent> future = transactionManager.sendTransaction(responses, sendTransaction, channel);
			Object result = future.thenApply((BlockEvent.TransactionEvent transactionEvent) -> {
				
				// 必须是有效交易事件
	    		checkArgument(transactionEvent.isValid(), "没有签名的交易事件");
	    		// 必须有签名
	    		checkNotNull(transactionEvent.getSignature(), "没有签名的交易事件");
	    		// 必须有交易区块事件发生
	    		BlockEvent blockEvent = checkNotNull(transactionEvent.getBlockEvent(), "交易事件的区块事件对象为空");
	    		
	    		try {
	    			System.out.println("成功交易，本次实例化交易ID：" +  transactionEvent.getTransactionID());
	    			checkArgument(StringUtils.equals(blockEvent.getChannelId(), channel.getName()), "事件名称和对应通道名称不一致");
	    			
	    			client.setUserContext(org.getUser(USER_NAME));

		    		transaction.setArgs(new String[] { "a" });
		    		transaction.setFunc("query");
					String account = transactionManager.queryChaincode(channel, transaction);
					System.out.println("account a: " + account);
					

					transaction.setArgs(new String[] { "b" });
					transaction.setFunc("query");
					account = transactionManager.queryChaincode(channel, transaction);
					System.out.println("account b: " + account);
					
				} catch (Exception e) {
		            throw new RuntimeException(e);
				}
	    		
				return "success";
			}).exceptionally(e -> {
                if (e instanceof CompletionException && e.getCause() != null) {
                    e = e.getCause();
                }
                
                if (e instanceof TransactionEventException) {
                    BlockEvent.TransactionEvent te = ((TransactionEventException) e).getTransactionEvent();
                    if (te != null) {
                        e.printStackTrace(System.err);
                        fail(format("Transaction with txid %s failed. %s", te.getTransactionID(), e.getMessage()));
                    }
                }

                e.printStackTrace(System.err);
                fail(format("Test failed with %s exception %s", e.getClass().getName(), e.getMessage()));

                return null;
            }).get(config.getTransactionWaitTime(), TimeUnit.SECONDS); 
			
			System.out.println(result);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testSetAmount() {
		int val = 50;
		
		try {
			Channel channel = getChannel();
			Organization org = config.getOrganization(orgName);
			
			TransactionEntity transaction = new TransactionEntity(id, CHAIN_CODE_LANG);
			transaction.setFunc("set");
			
			Map<String, byte[]> transientMap = Maps.newHashMap();
			transientMap.put("A", "a".getBytes(UTF_8));   // test using bytes as args. End2end uses Strings.
            transientMap.put("AVal", "500".getBytes(UTF_8));
            transientMap.put("B", "b".getBytes(UTF_8));
            transientMap.put("BVal", String.valueOf(200 + val).getBytes(UTF_8));
			
			transaction.setTransientMap(transientMap);
			
			client.setUserContext(org.getUser(USER_NAME));
			
			Collection<ProposalResponse> responses = transactionManager.invokeChaincode(channel, transaction);
			
			SendTransactionEntity sendTransaction = new SendTransactionEntity();
			sendTransaction.setUser(org.getUser(USER_NAME));
			
			CompletableFuture<TransactionEvent> future = transactionManager.sendTransaction(responses, sendTransaction, channel);
			Object result = future.thenApply((BlockEvent.TransactionEvent transactionEvent) -> {
				
				// 必须是有效交易事件
	    		checkArgument(transactionEvent.isValid(), "没有签名的交易事件");
	    		// 必须有签名
	    		checkNotNull(transactionEvent.getSignature(), "没有签名的交易事件");
	    		// 必须有交易区块事件发生
	    		BlockEvent blockEvent = checkNotNull(transactionEvent.getBlockEvent(), "交易事件的区块事件对象为空");
	    		
	    		try {
	    			System.out.println("成功交易，本次实例化交易ID：" +  transactionEvent.getTransactionID());
	    			checkArgument(StringUtils.equals(blockEvent.getChannelId(), channel.getName()), "事件名称和对应通道名称不一致");
				} catch (Exception e) {
		            throw new RuntimeException(e);
				}
	    		
				return "success";
			}).exceptionally(e -> {
                if (e instanceof CompletionException && e.getCause() != null) {
                    e = e.getCause();
                }
                
                if (e instanceof TransactionEventException) {
                    BlockEvent.TransactionEvent te = ((TransactionEventException) e).getTransactionEvent();
                    if (te != null) {
                        e.printStackTrace(System.err);
                        fail(format("Transaction with txid %s failed. %s", te.getTransactionID(), e.getMessage()));
                    }
                }

                e.printStackTrace(System.err);
                fail(format("Test failed with %s exception %s", e.getClass().getName(), e.getMessage()));

                return null;
            }).get(config.getTransactionWaitTime(), TimeUnit.SECONDS); 
			
			System.out.println(result);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testCollectionData() throws Exception {
		if (config.isFabricVersionAtOrAfter("1.3")) {
			Channel channel = getChannel();

			Set<String> expect = new HashSet<>(Arrays.asList("COLLECTION_FOR_A", "COLLECTION_FOR_B"));
			Set<String> got = new HashSet<>();

			CollectionConfigPackage packages = channel.queryCollectionsConfig(CHAIN_CODE_NAME, channel.getPeers().iterator().next(), config.getOrganization(orgName).getPeerAdmin());
			for (CollectionConfigPackage.CollectionConfig collectionConfig : packages.getCollectionConfigs()) {
				got.add(collectionConfig.getName());

			}
			assertEquals(expect, got);
		}
	}
}