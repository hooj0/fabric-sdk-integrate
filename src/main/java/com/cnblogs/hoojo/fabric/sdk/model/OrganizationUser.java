package com.cnblogs.hoojo.fabric.sdk.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cnblogs.hoojo.fabric.sdk.common.AbstractFabricObject;

import io.netty.util.internal.StringUtil;

public class OrganizationUser extends AbstractFabricObject implements User, Serializable {
	private static final long serialVersionUID = 8077132186383604355L;

	private final static Logger logger = LoggerFactory.getLogger(OrganizationUser.class);

	/** 获取由用户组织提供的会员服务提供商标识符 */
	private String mspId;
	/** 名称 */
	private String name;
	/** 角色 */
	private Set<String> roles;
	/** 账户  */
	private String account;
	/** */
	private String affiliation;
	/** 组织 */
	private String organization;
	/** 注册证书认证秘钥 */
	private String enrollmentSecret;
	/** 注册证书信息  */
	private Enrollment enrollment = null; // need access in test env.
	/** 键值存储 */
	private transient KeyValueFileStore fileStore;
	/** 键值存在名称 */
	private String storeKey;

	/**
	 * 构建一个用户，如果存在就从缓存中恢复，不存在就保存到缓存
	 * @param name 用户名
	 * @param org 组织名称
	 * @param fileStore 文件缓存
	 */
	public OrganizationUser(String name, String org, KeyValueFileStore fileStore) {
		this.name = name;

		this.organization = org;
		this.fileStore = fileStore;
		this.storeKey = toStoreKey(this.name, org);
		
		String member = fileStore.get(storeKey);
		if (StringUtils.isBlank(member)) {
			storeState();
		} else {
			restoreState();
		}
	}

	/**
	 * 转换用户键值存储的名称
	 * @author hoojo
	 * @createDate 2018年6月13日 上午11:37:49
	 * @param name 用户名
	 * @param org 组织名
	 * @return 存储的名称
	 */
	public static String toStoreKey(String name, String org) {
		return "user." + name + org;
	}
	
	/**
	 * 是否存储过，缓存中是否已存在该用户
	 * @author hoojo
	 * @createDate 2018年6月13日 上午11:39:54
	 * @param name 用户名
	 * @param org 组织名称
	 * @param fileStore kv对象
	 * @return boolean
	 */
	public static boolean isStored(String name, String org, KeyValueFileStore fileStore) {
		return fileStore.contains(toStoreKey(name, org));
	}

	public boolean isStored(String name, String org) {
		return fileStore.contains(toStoreKey(name, org));
	}

	/**
	 * 确定该名称是否已注册
	 */
	public boolean isRegistered() {
		return !StringUtil.isNullOrEmpty(enrollmentSecret);
	}

	/**
	 * 确定该名称是否已认证
	 */
	public boolean isEnrolled() {
		return this.enrollment != null;
	}

	/**
	 * 将此用户的状态保存到键值存储区
	 */
	public void storeState() {
		ByteArrayOutputStream bos = null;
		ObjectOutputStream oos = null;
		
		try {
			bos = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bos);
			
			oos.writeObject(this);
			oos.flush();
			
			fileStore.set(storeKey, Hex.toHexString(bos.toByteArray()));
			// 内存缓存
			fileStore.cacheMember(storeKey, this);
		} catch (IOException e) {
			logger.error("用户的状态保存到键值存储区异常", e);
		} finally {
			try {
				if (oos != null) oos.close();
				if (bos != null) bos.close();
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

	/**
	 * 从键值存储中恢复此用户的状态（如果找到）。 如果找不到，什么都不要做。
	 */
	public OrganizationUser restoreState() {
		
		String member = fileStore.get(storeKey);
		if (null == member) {
			return null;
		}
		
		ByteArrayInputStream bis = null;
		ObjectInputStream ois = null;
		try {
			// 用户被发现在关键值存储中，因此恢复状态
			byte[] serialized = Hex.decode(member);

			// 反序列化，解组对象
			bis = new ByteArrayInputStream(serialized);
			ois = new ObjectInputStream(bis);

			OrganizationUser state = (OrganizationUser) ois.readObject();
			if (state != null) {
				this.name = state.name;
				this.roles = state.roles;
				this.account = state.account;
				this.affiliation = state.affiliation;
				this.organization = state.organization;
				this.enrollmentSecret = state.enrollmentSecret;
				this.enrollment = state.enrollment;
				this.mspId = state.mspId;
				
				return this;
			}
		} catch (Exception e) {
			throw new RuntimeException(String.format("Could not restore state of member %s", this.name), e);
		} finally {
			try {
				if (bis != null) bis.close();
				if (bis != null) bis.close();
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
		
		return null;
	}

	public String getEnrollmentSecret() {
		return enrollmentSecret;
	}

	public void setEnrollmentSecret(String enrollmentSecret) {
		this.enrollmentSecret = enrollmentSecret;
		
		storeState();
	}

	public void setEnrollment(Enrollment enrollment) {
		this.enrollment = enrollment;
		
		storeState();
	}

	@Override
	public String getMspId() {
		return mspId;
	}

	public void setMspId(String mspID) {
		this.mspId = mspID;
		
		storeState();
	}
	
	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public Set<String> getRoles() {
		return this.roles;
	}

	public void setRoles(Set<String> roles) {
		this.roles = roles;
		
		storeState();
	}

	@Override
	public String getAccount() {
		return this.account;
	}

	public void setAccount(String account) {
		this.account = account;
		
		storeState();
	}

	@Override
	public String getAffiliation() {
		return this.affiliation;
	}

	public void setAffiliation(String affiliation) {
		this.affiliation = affiliation;
		
		storeState();
	}

	@Override
	public Enrollment getEnrollment() {
		return this.enrollment;
	}
}
