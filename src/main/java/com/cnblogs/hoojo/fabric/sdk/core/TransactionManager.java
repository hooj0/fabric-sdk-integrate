package com.cnblogs.hoojo.fabric.sdk.core;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.ChaincodeResponse.Status;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.QueryByChaincodeRequest;
import org.hyperledger.fabric.sdk.SDKUtils;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.TxReadWriteSetInfo;
import org.hyperledger.fabric.sdk.User;

import com.cnblogs.hoojo.fabric.sdk.config.DefaultConfiguration;
import com.cnblogs.hoojo.fabric.sdk.entity.TransactionEntity;
import com.google.common.base.Strings;


/**
 * Chaincode 交易 & 查询
 * @author hoojo
 * @createDate 2018年6月22日 下午4:50:12
 * @file ChaincodeTransactionManager.java
 * @package com.cnblogs.hoojo.fabric.sdk.core
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class TransactionManager extends AbstractTransactionManager {

	public TransactionManager(DefaultConfiguration config, HFClient client) {
		super(config, client);
	}
	
	/**
	 * 执行invoke调用chaincode业务
	 * @author hoojo
	 * @createDate 2018年6月15日 下午2:43:04
	 */
	public Collection<ProposalResponse> invokeChaincode(Channel channel, TransactionEntity transaction) throws Exception {
		return invokeChaincode(null, channel, transaction);
	}

	/**
	 * 执行invoke调用chaincode业务
	 * @author hoojo
	 * @createDate 2018年6月15日 下午2:43:04
	 */
	public Collection<ProposalResponse> invokeChaincode(User user, Channel channel, TransactionEntity transaction) throws Exception {
		logger.info("在通道：{}，发起调用Chaincode 交易业务: {}", channel.getName(), transaction.getChaincodeId());

		checkArgument(!Strings.isNullOrEmpty(transaction.getFunc()), "func 参数为必填项");
		checkArgument(!Objects.isNull(transaction.getArgs()), "args 参数为必填项");
		
		try {
            // 构建——交易提议请求，向所有对等节点发送
            TransactionProposalRequest request = client.newTransactionProposalRequest();
            request.setProposalWaitTime(config.getProposalWaitTime());
            request.setChaincodeLanguage(transaction.getLanguage());
            request.setChaincodeID(transaction.getChaincodeId());
            request.setFcn(transaction.getFunc());
            request.setArgs(transaction.getArgs());
            
            // 添加——到分类账的提案中的瞬时数据
            Map<String, byte[]> transientMap = new HashMap<>();
            transientMap.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8)); //Just some extra junk in transient map
            transientMap.put("method", "TransactionProposalRequest".getBytes(UTF_8)); // ditto
            transientMap.put("result", ":)".getBytes(UTF_8));  // This should be returned see chaincode why.

            if (transaction.getTransientMap() != null) {
            	transientMap.putAll(transaction.getTransientMap());
            }
            request.setTransientMap(transientMap);
            
            if (user != null) { // 使用特定用户
				request.setUserContext(user);
			}
            
            // 发送——交易请求
            Collection<ProposalResponse> responses = null;
            if (transaction.isSpecificPeers()) {
            	responses = channel.sendTransactionProposal(request, channel.getPeers()); // default
            } else {
            	responses = channel.sendTransactionProposal(request);
            }
            logger.info("向 channel.Peers节点——发起交易“提议”请求，参数: {}", request);
            
            Collection<ProposalResponse> successResponses = new LinkedList<>();
            Collection<ProposalResponse> failedResponses = new LinkedList<>();
            
			for (ProposalResponse response : responses) {
				if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
					logger.debug("交易成功 Txid: {} from peer {}", response.getTransactionID(), response.getPeer().getName());
					successResponses.add(response);
				} else {
					logger.debug("交易失败 Txid: {} from peer {}", response.getTransactionID(), response.getPeer().getName());
					failedResponses.add(response);
				}
			}
			
			// 检查请求——响应结果有效且不为空
			Collection<Set<ProposalResponse>> proposalConsistencySets = SDKUtils.getProposalConsistencySets(responses);
            if (proposalConsistencySets.size() != 1) {
                throw new RuntimeException(format("成功响应请求结果的数量等于1，实际响应数量： %d", proposalConsistencySets.size()));
            }
            logger.info("接收交易请求响应： {} ，Successful+verified: {}， Failed: {}", responses.size(), successResponses.size(), failedResponses.size());
            
            if (failedResponses.size() > 0) {
                ProposalResponse firstResponse = failedResponses.iterator().next();
                throw new RuntimeException("没有足够的背书节点调用: " + failedResponses.size() + "， endorser error: " + firstResponse.getMessage() + ". Was verified: " + firstResponse.isVerified());
            }
            
            ProposalResponse response = successResponses.iterator().next();
            // 对应上面构建的 transientMap->result
            byte[] chaincodeBytes = response.getChaincodeActionResponsePayload(); // 链码返回的数据
            String resultAsString = null;
            if (chaincodeBytes != null) {
                resultAsString = new String(chaincodeBytes, UTF_8);
            }
            checkArgument(StringUtils.equals(":)", resultAsString), "{} :和定义的账本数据不一致", resultAsString);
            checkState(response.getChaincodeActionResponseStatus() == Status.SUCCESS.getStatus(), "{}：非正常的响应状态码", response.getChaincodeActionResponseStatus());
            
            TxReadWriteSetInfo readWriteSetInfo = response.getChaincodeActionResponseReadWriteSetInfo();
            checkNotNull(readWriteSetInfo, "提议请求响应的读写集为空");
            checkArgument(readWriteSetInfo.getNsRwsetCount() > 0, "提议请求读写集数量为空");
            
            ChaincodeID codeId = response.getChaincodeID();
            checkNotNull(codeId, "提议请求响应ChaincodeID为空");
            checkArgument(StringUtils.equals(transaction.getChaincodeId().getName(), codeId.getName()), "chaincode 名称不一致");
            checkArgument(StringUtils.equals(transaction.getChaincodeId().getVersion(), codeId.getVersion()), "chaincode 版本不一致");
            
            final String path = codeId.getPath();
            if (transaction.getChaincodeId().getPath() == null) {
                checkArgument(StringUtils.isBlank(path), "chaincode Path不为空");
            } else {
            	checkArgument(StringUtils.equals(transaction.getChaincodeId().getPath(), path), "chaincode Path不一致");
            }

            return successResponses;
		} catch (Exception e) {
            logger.error("调用chaincode时发生异常：", e);
            throw new RuntimeException("调用chaincode时发生异常： " + e.getMessage());
		}
	}
	
	/**
	 * 执行 query 查询 chaincode 业务
	 * @author hoojo
	 * @createDate 2018年6月15日 下午2:44:29
	 */
	public String queryChaincode(Channel channel, TransactionEntity transaction) throws Exception {
		logger.info("在通道：{} 发起chaincode 查询业务：{}", channel.getName(), transaction.getChaincodeId());
		
		checkArgument(!Strings.isNullOrEmpty(transaction.getFunc()), "func 参数为必填项");
		checkArgument(!Objects.isNull(transaction.getArgs()), "args 参数为必填项");
		
		String payload = null;
		try {
			// 构建查询请求
			QueryByChaincodeRequest request = client.newQueryProposalRequest();
			request.setProposalWaitTime(config.getProposalWaitTime());
			request.setChaincodeLanguage(transaction.getLanguage());
			request.setChaincodeID(transaction.getChaincodeId());
			request.setFcn(transaction.getFunc());
			request.setArgs(transaction.getArgs());
			
			Map<String, byte[]> transientMap = new HashMap<>();
			transientMap.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(UTF_8));
			transientMap.put("method", "QueryByChaincodeRequest".getBytes(UTF_8));
			
			if (transaction.getTransientMap() != null) {
				transientMap.putAll(transaction.getTransientMap());
			}
            request.setTransientMap(transientMap);
            
            // 向所有Peer节点发送查询请求
            Collection<ProposalResponse> responses = null;
            if (transaction.isSpecificPeers()) {
            	responses = channel.queryByChaincode(request, channel.getPeers());
            } else {
            	responses = channel.queryByChaincode(request);
            }
            
            logger.info("向 channel.Peers——发起Chaincode查询请求：{}", request);
            
            for (ProposalResponse response : responses) {
                if (!response.isVerified() || response.getStatus() != Status.SUCCESS) {
                    throw new RuntimeException("查询失败， peer " + response.getPeer().getName() + "， status: " + response.getStatus() + ". Messages: " + response.getMessage() + ". Was verified : " + response.isVerified());
                } else {
                    payload = response.getProposalResponse().getResponse().getPayload().toStringUtf8();
                    logger.debug("查询来自对等点：{} ，返回结果：{}", response.getPeer().getName(), payload);
                }
            }
		} catch (Exception e) {
			logger.error("调用chaincode时发生异常：", e);
            throw new RuntimeException("调用chaincode时发生异常： " + e.getMessage());
		}

		return payload;
	}
}
