package org.gnuhpc.bigdata.controller;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.concurrent.TimeoutException;
import joptsimple.internal.Strings;
import kafka.admin.AdminClient.ConsumerGroupSummary;
import kafka.admin.AdminClient.ConsumerSummary;
import kafka.cluster.Broker;
import kafka.common.TopicAndPartition;
import lombok.extern.log4j.Log4j;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.ApiException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.gnuhpc.bigdata.constant.ConsumerType;
import org.gnuhpc.bigdata.constant.GeneralResponseState;
import org.gnuhpc.bigdata.model.*;
import org.gnuhpc.bigdata.service.KafkaAdminService;
import org.gnuhpc.bigdata.service.KafkaProducerService;
import org.gnuhpc.bigdata.validator.ConsumerGroupExistConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Created by gnuhpc on 2017/7/16.
 */
@Log4j
@RequestMapping("/kafka")
@RestController
public class KafkaController {

  @Autowired
  private KafkaAdminService kafkaAdminService;

  @Autowired
  private KafkaProducerService kafkaProducerService;

  @GetMapping(value = "/brokers")
  @ApiOperation(value = "List brokers in this cluster")
  public List<BrokerInfo> listBrokers() {
    return kafkaAdminService.listBrokers();
  }

  @GetMapping(value = "/controller")
  @ApiOperation(value = "Get controller id in this cluster")
  public int getControllerId() {
    return kafkaAdminService.getControllerId();
  }

  @GetMapping("/topics")
  @ApiOperation(value = "List topics")
  public List<String> listTopics() throws InterruptedException, ExecutionException {
    return kafkaAdminService.listTopics();
  }

  @GetMapping("/topicsbrief")
  @ApiOperation(value = "List topics Brief")
  public List<TopicBrief> listTopicBrief() throws InterruptedException, ExecutionException {
    return kafkaAdminService.listTopicBrief();
  }

  @PostMapping(value = "/topics/create", consumes = "application/json")
  @ResponseStatus(HttpStatus.CREATED)
  @ApiOperation(value = "Create a topic")
  @ApiParam(value = "if reassignStr set, partitions and repli-factor will be ignored.")
  public TopicMeta createTopic(@RequestBody TopicDetail topic,
      @RequestParam(required = false) String reassignStr) {
    return kafkaAdminService.createTopic(topic, reassignStr);
  }

  @ApiOperation(value = "Tell if a topic exists")
  @GetMapping(value = "/topics/{topic}/exist")
  public boolean existTopic(@PathVariable String topic) {
    return kafkaAdminService.existTopic(topic);
  }

  @PostMapping(value = "/topics/{topic}/write", consumes = "text/plain")
  @ResponseStatus(HttpStatus.CREATED)
  @ApiOperation(value = "Write a message to the topic, for testing purpose")
  public GeneralResponse writeMessage(@PathVariable String topic, @RequestBody String message) {
//        kafkaProducerService.send(topic, message);
    return GeneralResponse.builder().state(GeneralResponseState.success)
        .msg(message + " has been sent").build();
  }

  @GetMapping(value = "/consumer/{topic}/{partition}/{offset}")
  @ApiOperation(value = "Get the message from the offset of the partition in the topic" +
      ", decoder is not supported yet")
  public String getMessage(@PathVariable String topic,
      @PathVariable int partition,
      @PathVariable long offset, @RequestParam(required = false) String decoder) {
    return kafkaAdminService.getRecordByOffset(topic, partition, offset, decoder, "").getValue();
  }

  @GetMapping(value = "/topics/{topic}")
  @ApiOperation(value = "Describe a topic by fetching the metadata and config")
  public TopicMeta describeTopic(@PathVariable String topic) {
    return kafkaAdminService.describeTopic(topic);
  }

  @DeleteMapping(value = "/topics")
  @ApiOperation(value = "Delete a topic list (you should enable topic deletion")
  public Map<String, GeneralResponse> deleteTopicList(@RequestParam List<String> topicList) {
    return kafkaAdminService.deleteTopicList(topicList);
  }

  @PutMapping(value = "/topics/{topic}/conf")
  @ApiOperation(value = "Update topic configs")
  public Collection<ConfigEntry> updateTopicConfig(@PathVariable String topic,
      @RequestBody Properties props)
      throws InterruptedException, ExecutionException {
    return kafkaAdminService.updateTopicConf(topic, props);
  }

  @GetMapping(value = "/topics/{topic}/conf")
  @ApiOperation(value = "Get topic configs")
  public Collection<ConfigEntry> getTopicConfig(@PathVariable String topic)
      throws InterruptedException, ExecutionException {
    return kafkaAdminService.getTopicConf(topic);
  }

  @GetMapping(value = "/topics/{topic}/conf/{key}")
  @ApiOperation(value = "Get topic config by key")
  public Properties getTopicConfigByKey(@PathVariable String topic,
      @PathVariable String key) throws InterruptedException, ExecutionException {
    return kafkaAdminService.getTopicConfByKey(topic, key);
  }

  @PutMapping(value = "/topics/{topic}/conf/{key}={value}")
  @ApiOperation(value = "Update a topic config by key")
  public Collection<ConfigEntry> updateTopicConfigByKey(@PathVariable String topic,
      @PathVariable String key,
      @PathVariable String value) throws InterruptedException, ExecutionException {
    return kafkaAdminService.updateTopicConfByKey(topic, key, value);
  }

  @PostMapping(value = "/partitions/add")
  @ApiOperation(value = "Add partitions to the topics")
  public Map<String, GeneralResponse> addPartition(@RequestBody List<AddPartition> addPartitions) {
    return kafkaAdminService.addPartitions(addPartitions);
  }

  /*
  @PostMapping(value = "/partitions/add")
  @ApiOperation(value = "Add a partition to the topic")
  public TopicMeta addPartition(@RequestBody AddPartition addPartition) {
      String topic = addPartition.getTopic();
      isTopicExist(topic);

      if (addPartition.getReplicaAssignment() != null && !addPartition.getReplicaAssignment().equals("") && addPartition.getReplicaAssignment().split(",").length
              != addPartition.getNumPartitionsAdded()) {
          throw new InvalidTopicException("Topic " + topic + ": num of partitions added not equal to manual reassignment str!");
      }

      if (addPartition.getNumPartitionsAdded() == 0) {
          throw new InvalidTopicException("Num of paritions added must be specified and should not be 0");
      }
      return kafkaAdminService.addPartition(topic, addPartition);
  }

  @PostMapping(value = "/partitions/reassign/generate")
  @ApiOperation(value = "Generate plan for the partition reassignment")
  public List<String> generateReassignPartitions(@RequestBody ReassignWrapper reassignWrapper) {
      return kafkaAdminService.generateReassignPartition(reassignWrapper);

  }

  @PutMapping(value = "/partitions/reassign/execute")
  @ApiOperation(value = "Execute the partition reassignment")
  public Map<TopicAndPartition, Integer> executeReassignPartitions(
          @RequestBody String reassignStr) {
      return kafkaAdminService.executeReassignPartition(reassignStr);
  }

  @PutMapping(value = "/partitions/reassign/check")
  @ApiOperation(value = "Check the partition reassignment process")
  @ApiResponses(value = {@ApiResponse(code = 1, message = "Reassignment Completed"),
          @ApiResponse(code = 0, message = "Reassignment In Progress"),
          @ApiResponse(code = -1, message = "Reassignment Failed")})
  public Map<TopicAndPartition, Integer> checkReassignPartitions(@RequestBody String reassignStr) {
      return kafkaAdminService.checkReassignStatus(reassignStr);
  }
  */
  @GetMapping(value = "/consumergroups")
  @ApiOperation(value = "List all consumer groups from zk and kafka")
  public Map<String, Set<String>> listAllConsumerGroups(
      @RequestParam(required = false) ConsumerType type,
      @RequestParam(required = false) String topic) {
    if (topic != null) {
      return kafkaAdminService.listConsumerGroupsByTopic(topic, type);
    } else {
      return kafkaAdminService.listAllConsumerGroups(type);
    }
  }

  @GetMapping(value = "/consumergroups/{consumerGroup}/{type}/topic")
  @ApiOperation(value = "Get the topics involved of the specify consumer group")
  public Set<String> listTopicByCG(@PathVariable String consumerGroup,
      @PathVariable ConsumerType type) {
    return kafkaAdminService.listTopicsByCG(consumerGroup, type);
  }

  @GetMapping(value = "/consumergroups/{consumerGroup}/meta")
  @ApiOperation(value = "Get the meta data of the specify new consumer group, including state, coordinator, assignmentStrategy, members")
  public ConsumerGroupMeta getConsumerGroupMeta(@PathVariable String consumerGroup) {
    if (kafkaAdminService.isNewConsumerGroup(consumerGroup)) {
      return kafkaAdminService.getConsumerGroupMeta(consumerGroup);
    }

    throw new ApiException("New consumer group:" + consumerGroup + " non-exist.");
  }

  @GetMapping(value = "/consumergroups/{consumerGroup}/{type}/topic/{topic}")
  @ApiOperation(value = "Describe consumer groups by topic, showing lag and offset")
  public List<ConsumerGroupDesc> describeCGByTopic(
      @ConsumerGroupExistConstraint @PathVariable String consumerGroup,
      @PathVariable ConsumerType type,
      @PathVariable String topic) throws InterruptedException, ExecutionException {
    if (!Strings.isNullOrEmpty(topic)) {
      existTopic(topic);
    } else {
      throw new ApiException("Topic must be set!");
    }
    if (type != null && type == ConsumerType.NEW) {
      return kafkaAdminService.describeNewCGByTopic(consumerGroup, topic);
    }

    if (type != null && type == ConsumerType.OLD) {
      return kafkaAdminService.describeOldCGByTopic(consumerGroup, topic);
    }

    throw new ApiException("Unknown type specified!");
  }

  @GetMapping(value = "/consumergroups/{consumerGroup}/{type}")
  @ApiOperation(value = "Describe consumer groups, showing lag and offset, may be slow if multi topic are listened")
  public Map<String, List<ConsumerGroupDesc>> describeCG(
      @ConsumerGroupExistConstraint @PathVariable String consumerGroup,
      @PathVariable ConsumerType type) {
    return kafkaAdminService.describeConsumerGroup(consumerGroup, type);
  }

  @PutMapping(value = "/consumergroup/{consumergroup}/{type}/topic/{topic}/{partition}/{offset}")
  @ApiOperation(value = "Reset consumer group offset, earliest/latest can be used. Support reset by time for new consumer group, pass a parameter that satisfies yyyy-MM-dd HH:mm:ss to offset.")
  public GeneralResponse resetOffset(@PathVariable String topic,
      @PathVariable int partition,
      @PathVariable String consumergroup,
      @PathVariable @ApiParam(
          value = "[earliest/latest/{long}/yyyy-MM-dd HH:mm:ss] can be supported. The date type is only valid for new consumer group.") String offset,
      @PathVariable ConsumerType type) throws InterruptedException, ExecutionException {
    return kafkaAdminService.resetOffset(topic, partition, consumergroup, type, offset);
  }


  @GetMapping(value = "/consumergroup/{consumergroup}/{type}/topic/{topic}/lastcommittime")
  public Map<String, Map<Integer, Long>> getLastCommitTimestamp(
      @PathVariable String consumergroup,
      @PathVariable String topic,
      @PathVariable ConsumerType type) {
    return kafkaAdminService.getLastCommitTime(consumergroup, topic, type);
  }

  @DeleteMapping(value = "/consumergroup/{consumergroup}/{type}")
  @ApiOperation(value = "Delete Consumer Group")
  public GeneralResponse deleteOldConsumerGroup(@PathVariable String consumergroup,
      @PathVariable ConsumerType type) {
    return kafkaAdminService.deleteConsumerGroup(consumergroup, type);
  }

  @GetMapping(value = "/health")
  @ApiOperation(value = "Check the cluster health.")
  public HealthCheckResult healthCheck() {
    return kafkaAdminService.healthCheck();
  }
}
