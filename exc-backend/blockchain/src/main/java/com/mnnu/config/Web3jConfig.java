package com.mnnu.config;

import okhttp3.OkHttpClient; // 导入 OkHttpClient
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.StaticGasProvider;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit; // 导入 TimeUnit

/**
 * Web3j 配置
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

    // 添加 Web3j 连接和读取超时配置
    @Value("${web3j.connect-timeout:30000}") // 默认 30 秒
    private long connectTimeout;

    @Value("${web3j.read-timeout:120000}") // 默认 120 秒
    private long readTimeout;

    @Bean
    public Web3j web3j() {
        // 1. 创建 OkHttpClient 并设置更长的超时时间
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS) // 连接超时
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)     // 读取超时
                .writeTimeout(connectTimeout, TimeUnit.MILLISECONDS) // 写入超时（通常和连接超时一致）
                .build();

        // 2. 使用这个自定义的 OkHttpClient 构建 HttpService
        HttpService httpService = new HttpService(nodeUrl, okHttpClient, false); // 最后一个参数是是否包含公共参数，一般设为 false

        // 3. 构建 Web3j 实例
        return Web3j.build(httpService);
    }

    /**
     * 创建 Credentials 实例（用于签名交易）
     */
    @Bean
    public Credentials credentials() {
        // 使用私钥字符串直接创建 Credentials
        return Credentials.create(privateKey);
    }

    /**
     * 创建交易管理器
     * 管理交易的签名和发送
     */
    @Bean("web3jTransactionManager")
    public TransactionManager transactionManager(Web3j web3j, Credentials credentials) {
        return new RawTransactionManager(web3j, credentials, chainId);
    }

    /**
     * 创建 Gas 提供者
     * 提供交易的 Gas 价格设置
     * 控制交易手续费和执行优先级
     */
    @Bean
    public ContractGasProvider contractGasProvider() {
        // 使用 StaticGasProvider，它会精确地使用我们从配置文件中读取的值。
        return new StaticGasProvider(BigInteger.valueOf(gasPrice), BigInteger.valueOf(gasLimit));
    }
}
