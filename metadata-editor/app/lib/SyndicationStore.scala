package lib

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.gu.mediaservice.lib.aws.InstanceAwareDynamoDB
import com.gu.mediaservice.model.SyndicationRights
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

class SyndicationStore(client: AmazonDynamoDBAsync, client2: DynamoDbClient, tableName: String)
  extends InstanceAwareDynamoDB[SyndicationRights](client, client2, tableName)
