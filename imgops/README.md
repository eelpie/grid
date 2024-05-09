## imgops

Kahuna requests scaled versions of original images on demand.

It does this by calling signed S3 urls through an image resizing proxy.
We're not sure how exactly the Guardian implements this. They might be using an image CDN.

imgops is a naive nginx based implementation of the same interface.

It is implemented using the nginx image filter pointed at the original image bucket.
Because Media API provides signed URLs the proxy does not need to consider auth.

This is a very greedy operation.
Kahuna requests resized images to exactly match window sizes; there is a potential 1 to 1 between page views and
fetches of the full original image from S3.

This is probably fine when running within AWS but has latency and data cost concerns if running outside of AWS!
