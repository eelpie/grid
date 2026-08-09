package com.gu.mediaservice.lib.aws

import software.amazon.awssdk.services.s3.S3Client

case class S3Bucket(bucket: String, client: S3Client)
