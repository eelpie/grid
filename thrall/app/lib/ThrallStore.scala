package lib

import com.gu.mediaservice.lib

class ThrallStore(config: ThrallConfig) extends lib.ImageIngestOperations(config.imageBucket, config.thumbnailBucket, config.embeddingSourcesBucket, config, config.isVersionedS3)
