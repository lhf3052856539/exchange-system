package com.mnnu.config; // 确保这个包名和你的项目结构匹配

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials; // <<--- 导入 Credentials 类
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.StaticGasProvider;

import java.math.BigInteger;

/**
 * Web3j配置
 * 用于连接以太坊区块链节点并提供交易管理能力
 */
@Configuration
public class Web3jConfig {

    @Value("${web3j.node-url}")
    private String nodeUrl;

    @Value("${web3j.private-key}")
    private String privateKey;

    @Value("${web3j.gas-price}")
    private long gasPrice;

    @Value("${web3j.gas-limit}")
    private long gasLimit;

    @Value("${web3j.network-id}")
    private long chainId;

    @Bean
    public Web3j web3j() {
        return Web3j.build(new HttpService(nodeUrl));
    }

    /**
     * 创建交易管理器
     * 管理交易的签名和发送
     */
    @Bean("web3jTransactionManager")
    public TransactionManager transactionManager(Web3j web3j) {
        // 关键修正点：
        // 1. 使用私钥字符串创建一个 Credentials 对象。
        //    Credentials 是 web3j 中用于封装身份凭证（私钥、公钥、地址）的安全对象。
        Credentials credentials = Credentials.create(privateKey);

        // 2. 将 Credentials 对象传递给 RawTransactionManager 的构造函数。
        //    这才是 web3j 库推荐和支持的正确用法。
        return new RawTransactionManager(web3j, credentials, chainId);
    }

    /**
     * 创建Gas提供者
     * 提供交易的 Gas 价格设置
     * 控制交易手续费和执行优先级
     */
    @Bean
    public ContractGasProvider contractGasProvider() {
        // 使用 StaticGasProvider，它会精确地使用我们从配置文件中读取的值。
        return new StaticGasProvider(BigInteger.valueOf(gasPrice), BigInteger.valueOf(gasLimit));
    }
}

