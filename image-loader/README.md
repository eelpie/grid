# image-loader

image-loader provides an endpoint for submitting new media to the media service. It is responsible for storing the
submitted media, and raising a notification (via SNS) of the location and metadata of the media.


## Build image

Probably needs image processing tools to be installed in the image.


## Testing image upload

```
curl -XPOST --data-binary "@my-image.jpg" http://localhost:9000/images
```

Needs Dynano table
{"errorKey":"internal-error","errorMessage":"Unhandled error: Requested resource not found (Service: AmazonDynamoDBv2; Status Code: 400; Error Code: ResourceNotFoundException
