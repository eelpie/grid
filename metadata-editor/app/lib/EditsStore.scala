package lib

import com.gu.mediaservice.lib.aws.InstanceAwareDynamoDB
import com.gu.mediaservice.model.Edits
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

class EditsStore(client2: DynamoDbClient, tableName: String) extends InstanceAwareDynamoDB[Edits](client, client2, tableName, Some(Edits.LastModified))
