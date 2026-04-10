package com.mnnu.mapper;
/**
 * 交易记录Mapper
 */

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mnnu.entity.TradeRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface TradeMapper extends BaseMapper<TradeRecordEntity> {

    /**
     * 根据交易ID查询
     */
    @Select("SELECT * FROM t_trade_record WHERE trade_id = #{tradeId}")
    TradeRecordEntity selectByTradeId(@Param("tradeId") String tradeId);

    /**
     * 统计总交易量（UT）
     * 只统计已完成的交易
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM t_trade_record WHERE status = 3")
    Long selectTotalUTVolume();

    /**
     * 查询用户进行中的交易
     */
    @Select("SELECT * FROM t_trade_record WHERE (party_a = #{address} OR party_b = #{address}) " +
            "AND status IN (0,1,2,3,5) ORDER BY create_time DESC")
    List<TradeRecordEntity> selectActiveTrades(@Param("address") String address);

    /**
     * 查询待处理的交易（需要确认）
     */
    @Select("SELECT * FROM t_trade_record WHERE (party_a = #{address} OR party_b = #{address}) " +
            "AND status = #{status} ORDER BY create_time DESC")
    List<TradeRecordEntity> selectPendingTrades(@Param("address") String address,
                                                @Param("status") Integer status);

    /**
     * 更新交易状态
     */
    @Update("UPDATE t_trade_record SET status = #{status}, update_time = NOW() " +
            "WHERE trade_id = #{tradeId}")
    int updateStatus(@Param("tradeId") String tradeId, @Param("status") Integer status);

    /**
     * 确认A方转账
     */
    @Update("UPDATE t_trade_record SET is_party_a_confirmed = 1, party_a_confirm_time = NOW(), " +
            "status = 2, update_time = NOW() WHERE trade_id = #{tradeId}")
    int confirmPartyA(@Param("tradeId") String tradeId);

    /**
     * 确认B方转账
     */
    @Update("UPDATE t_trade_record SET is_party_b_confirmed = 1, party_b_confirm_time = NOW(), " +
            "status = 4, complete_time = NOW(), update_time = NOW() WHERE trade_id = #{tradeId}")
    int confirmPartyB(@Param("tradeId") String tradeId);

    /**
     * 标记争议
     */
    @Update("UPDATE t_trade_record SET is_disputed = 1, disputed_party = #{disputedParty}, " +
            "status = 5, update_time = NOW() WHERE trade_id = #{tradeId}")
    int markDisputed(@Param("tradeId") String tradeId,
                     @Param("disputedParty") String disputedParty);

    /**
     * 统计总交易量
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM t_trade_record WHERE status = 4")
    Long sumCompletedAmount();

    /**
     * 统计指定年份的交易总量
     */
    @Select("SELECT COALESCE(SUM(amount_a), 0) FROM trade_record WHERE YEAR(create_time) = #{year} AND status = 2")
    Long selectSumAmountByYear(@Param("year") int year);
}
