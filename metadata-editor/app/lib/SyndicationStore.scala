package lib

import com.gu.mediaservice.lib.aws.DynamoDB
import com.gu.mediaservice.model.SyndicationRights
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

class SyndicationStore(client2: DynamoDbClient, tableName: String)
  extends DynamoDB[SyndicationRights](client2, tableName)
