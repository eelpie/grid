package com.gu.mediaservice.lib.aws

import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

case class S3Bucket(bucket: String, endPoint: String, usesPathStyleURLs: Boolean, client: S3Client, presigner: S3Presigner)
