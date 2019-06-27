/**
 * Copyright 2014-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webank.webasebee.crawler.service;

import java.io.IOException;
import java.math.BigInteger;
import java.text.ParseException;
import java.util.List;

import javax.annotation.PostConstruct;

import org.fisco.bcos.web3j.protocol.Web3j;
import org.fisco.bcos.web3j.protocol.core.methods.response.BcosBlock.Block;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.webank.webasebee.config.SystemEnvironmentConfig;
import com.webank.webasebee.constants.BlockForkConstants;

import lombok.extern.slf4j.Slf4j;

/**
 * CommonCrawlerService is the main control process. The driven method is handle().
 *
 * @Description: CommonCrawlerService
 * @author maojiayu
 * @data Dec 28, 2018 5:25:35 PM
 *
 */
@Service
@Slf4j
@EnableScheduling
@ConditionalOnProperty(name = "system.multiLiving", havingValue = "false")
public class CommonCrawlerService {

    @Autowired
    private Web3j web3j;
    @Autowired
    private SystemEnvironmentConfig systemEnvironmentConfig;
    @Autowired
    private BlockTaskPoolService blockTaskPoolService;
    @Autowired
    private BlockAsyncService blockAsyncService;
    @Autowired
    private BlockSyncService blockSyncService;
    @Autowired
    private BlockIndexService blockIndexService;

    private long startBlockNumber;

    private boolean signal = true;

    @PostConstruct
    public void setStartBlockNumber() throws ParseException, IOException, InterruptedException {
        startBlockNumber = blockIndexService.getStartBlockIndex();
        log.info("Start succeed, and the block number is {}", startBlockNumber);
    }

    public long getHeight(long height) {
        return height > startBlockNumber ? height : startBlockNumber;
    }

    /**
     * The key driving entrance of single instance depot: 1. check timeout txs and process errors; 2. produce tasks; 3.
     * consume tasks; 4. check the fork status; 5. rollback; 6. continue and circle;
     * 
     */
    public void handle() {
        try {
            log.info("The max block height threshold is {}", systemEnvironmentConfig.getMaxBlockHeightThreshold());
            while (signal) {
                long total = getCurrentBlockHeight();
                long height = getHeight(blockTaskPoolService.getTaskPoolHeight());
                log.info("Current depot status: {} of {}, and try to process block {}", height - 1, total, height);
                blockTaskPoolService.checkTimeOut();
                blockTaskPoolService.processErrors();
                // control the batch unit number
                long end = height + systemEnvironmentConfig.getCrawlBatchUnit() - 1;
                long endNo = total < end ? total : end;
                boolean certainty = endNo + 1 < total - BlockForkConstants.MAX_FORK_CERTAINTY_BLOCK_NUMBER;
                if (!certainty) {
                    blockTaskPoolService.checkForks(total);
                    blockTaskPoolService.checkTaskNumber(startBlockNumber, total);
                }
                if (height <= endNo) {
                    log.info("Try to sync block number {} to {} of {}", height, endNo, total);
                    blockTaskPoolService.prepareTask(height, endNo, certainty);
                } else {
                    // single circle sleep time is read from the application.properties
                    Thread.sleep(systemEnvironmentConfig.getFrequency() * 1000);
                }
                List<Block> taskList = blockSyncService.fetchData(systemEnvironmentConfig.getCrawlBatchUnit());
                for (Block b : taskList) {
                    blockAsyncService.handleSingleBlock(b, total);

                }
            }
        } catch (IOException e) {
            log.error("depot IOError, {}", e.getMessage());
        } catch (InterruptedException e) {
            log.error("depot InterruptedException, {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    public long getCurrentBlockHeight() throws IOException {
        BigInteger blockNumber = web3j.getBlockNumber().send().getBlockNumber();
        long total = blockNumber.longValue();
        log.debug("Current chain block number is:{}", blockNumber);
        return total;
    }

}
