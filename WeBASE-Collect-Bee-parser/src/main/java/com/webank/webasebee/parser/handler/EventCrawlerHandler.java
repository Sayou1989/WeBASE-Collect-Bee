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
package com.webank.webasebee.parser.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.fisco.bcos.web3j.protocol.Web3j;
import org.fisco.bcos.web3j.protocol.core.methods.response.BcosBlock.Block;
import org.fisco.bcos.web3j.protocol.core.methods.response.BcosBlock.TransactionResult;
import org.fisco.bcos.web3j.protocol.core.methods.response.BcosTransactionReceipt;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.webank.webasebee.common.bo.data.EventBO;
import com.webank.webasebee.parser.crawler.face.BcosEventCrawlerInterface;

/**
 * EventCrawlerHandler
 *
 * @Description: EventCrawlerHandler
 * @author maojiayu
 * @data Jul 3, 2019 10:00:38 AM
 *
 */
@Service
public class EventCrawlerHandler {
    @Autowired
    private Web3j web3j;
    @Autowired
    private Map<String, BcosEventCrawlerInterface> bcosEventCrawlerMap;

    public List<EventBO> crawl(Block block) throws IOException {
        List<EventBO> boList = new ArrayList<>();
        List<TransactionResult> transactionResults = block.getTransactions();
        for (TransactionResult result : transactionResults) {
            BcosTransactionReceipt bcosTransactionReceipt = web3j.getTransactionReceipt((String) result.get()).send();
            Optional<TransactionReceipt> opt = bcosTransactionReceipt.getTransactionReceipt();
            if (opt.isPresent()) {
                TransactionReceipt tr = opt.get();
                bcosEventCrawlerMap.forEach((k, v) -> {
                    boList.addAll(v.handleReceipt(tr, block.getTimestamp()));
                });
            }
        }
        return boList;
    }

}
