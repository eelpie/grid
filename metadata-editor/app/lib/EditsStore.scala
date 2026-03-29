package lib

import com.gu.mediaservice.lib.aws.DynamoDB
import com.gu.mediaservice.model.Edits
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

class EditsStore(client: DynamoDbClient, tableName: String) extends DynamoDB[Edits](client, tableName, Some(Edits.LastModified))
