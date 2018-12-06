package com.cnblogs.hoojo.fabric.sdk.examples;

import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;

import com.cnblogs.hoojo.fabric.sdk.model.Organization;
import com.cnblogs.hoojo.fabric.sdk.model.OrganizationUser;
import com.cnblogs.hoojo.fabric.sdk.persistence.KeyValueFileStore;

/**
 * `end 2 end` JavaSDK use Idemix 
 * 使用Idemix凭据进行交易，依赖 End2End 示例，在End2End示例运行后可以运行此示例。
 * @author hoojo
 * @createDate 2018年12月6日 上午9:55:56
 * @file End2EndIdemixExamples.java
 * @package com.cnblogs.hoojo.fabric.sdk.examples
 * @project fabric-sdk-integrate
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class End2EndIdemixExamples extends End2EndExamples {

	static {
		USER_NAME = "idemixUser";
	}
	
	protected OrganizationUser registerAndEnrollUser(KeyValueFileStore store, Organization org) throws Exception {
		logger.info("普通用户——注册和认证……");
		
		HFCAClient ca = org.getCAClient();
		
		// 从缓存或store中获取用户
		OrganizationUser user = store.getMember(USER_NAME, org.getName());
		if (!user.isRegistered()) { // 未注册
			// 用户注册
			final RegistrationRequest request = new RegistrationRequest(user.getName(), "org1.department1");
			logger.trace("register request: {}",  json(request));
			
			// 利用管理员权限进行普通user注册
			String secret = ca.register(request, org.getAdmin());
			logger.trace("用户 {} 注册，秘钥：{}", user, secret);
			
			user.setAffiliation(request.getAffiliation());
			user.setEnrollmentSecret(secret);
		}
		
		if (!user.isEnrolled()) { // 未认证
			// 用户认证
			Enrollment enrollment = ca.enroll(user.getName(), user.getEnrollmentSecret());
			logger.trace("用户：{} 进行认证: {}", user.getName(), json(enrollment));
			
			user.setEnrollment(enrollment);
			user.setMspId(org.getMSPID());
		}
		
		// If running version 1.3, then get Idemix credential
        if (config.isFabricVersionAtOrAfter("1.3")) {
            String mspID = "idemixMSPID1";
            if (org.getName().contains("Org2")) {
                mspID = "idemixMSPID2";
            }
            //user.setEnrollment(ca.idemixEnroll(user.getEnrollment(), mspID));
            user.setIdemixEnrollment(ca.idemixEnroll(user.getEnrollment(), mspID));
        }
		
		return user;
	}
}
