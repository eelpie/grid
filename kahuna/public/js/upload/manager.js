import angular from 'angular';

import Filehash from 'filehash/src/filehash';

var upload = angular.module('kahuna.upload.manager', []);

upload.factory('uploadManager',
               ['$q', '$window', 'fileUploader',
                function($q, $window, fileUploader) {

    var jobs = new Set();
    var completedJobs = new Set();



    function createJobItem(file, mediaId, preSignedUrl) {
        return {
            name: file.name,
            size: file.size,
            dataUrl: $window.URL.createObjectURL(file),
            resourcePromise: fileUploader.upload(file, mediaId, preSignedUrl)
        };
    }
    function createUriJobItem(fileUri) {
        var request = fileUploader.loadUriImage(fileUri);

        return {
            name: fileUri,
            dataUrl: fileUri,
            resourcePromise: request
        };
    }

    async function createJobItems(_files){

      const maybeUploadLimitInBytes = window._clientConfig.maybeUploadLimitInBytes;
      const maybeFilesAboveSizeLimit = !!maybeUploadLimitInBytes && _files.filter(file => file.size > maybeUploadLimitInBytes);

      if (maybeFilesAboveSizeLimit && maybeFilesAboveSizeLimit.length > 0){
        alert(`The following files will be skipped as they are above the size limit of ${maybeUploadLimitInBytes / 1_000_000}MB:\n${
          maybeFilesAboveSizeLimit.map(file => file.name).join("\n")
        }`);
      }

      const files = maybeFilesAboveSizeLimit && maybeFilesAboveSizeLimit.length > 0
        ? _files.filter(file => !maybeFilesAboveSizeLimit.includes(file))
        : _files;

      if (window._clientConfig.shouldUploadStraightToBucket) {
        const mediaIdToFileMap = Object.fromEntries(
          await Promise.all(
            files.map(file =>
              Filehash.hash(file, "SHA-1").then(mediaId => [mediaId, file])
            )
          )
        );

        const preSignedPutUrls = await fileUploader.prepare(
          Object.fromEntries(Object.entries(mediaIdToFileMap).map(([mediaId, file])=> [mediaId, file.name]))
        );

        return Object.entries(mediaIdToFileMap).map(([mediaId, file]) => createJobItem(file, mediaId, preSignedPutUrls[mediaId]));
      }

      return files.map(file => createJobItem(file));
    }

    async function upload(files) {

        const jobItems = await createJobItems(files);
        const promises = jobItems.map(jobItem => jobItem.resourcePromise);

        jobs.add(jobItems);

        // once all `jobItems` in a job are complete, remove it
        // TODO: potentially move these to a `completeJobs` `Set`
        $q.all(promises).finally(() => {
          completedJobs.add(jobItems);
          jobs.delete(jobItems);
          jobItems.map(jobItem => {
            $window.URL.revokeObjectURL(jobItem.dataUrl);
          });
        });
    }

    function uploadUri(uri) {
        var jobItem = createUriJobItem(uri);
        var promise = jobItem.resourcePromise;
        var job = [jobItem];

        jobs.add(job);

        // once all `jobItems` in a job are complete, remove it
        // TODO: potentially move these to a `completeJobs` `Set`
        promise.finally(() => jobs.delete(job));
    }

    function getLatestRunningJob() {
        return jobs.values().next().value;
    }

    function getJobs() {
        return jobs;
    }

    function getCompletedJobs() {
        return completedJobs;
    }

    return {
        upload,
        uploadUri,
        getLatestRunningJob,
        getJobs,
        getCompletedJobs
    };
}]);


upload.factory('fileUploader',
               ['$q', 'loaderApi',
                function($q, loaderApi) {

    function prepare(mediaIdToFilenameMap) {
        return loaderApi.prepare(mediaIdToFilenameMap);
    }

    async function upload(file, mediaId, preSignedUrl) {

      if (!preSignedUrl){
        return uploadFile(file, {filename: file.name});
      }

      const s3Response = await fetch(preSignedUrl, {
        method: "PUT",
        body: file,
        headers: {
          "x-amz-meta-media-id": mediaId
        }
      });

      if (s3Response.ok) {
        await markAsQueued(mediaId).catch(err => {
          console.error("Failed to mark as queued", err);
        });
        return buildUploadStatusResource(mediaId);
      }
      throw new Error(`Failed to upload to S3: ${s3Response.status} ${s3Response.statusText}`);
    }

    function uploadFile(file, uploadInfo) {
        return loaderApi.load(file, uploadInfo);
    }

    function loadUriImage(fileUri) {
        return loaderApi.import(fileUri);
    }

    function buildUploadStatusEndpoint(mediaId) {
      return loaderApi.getLoaderRoot().follow('uploadStatus', {id: mediaId});
    }

    function buildUploadStatusResource(mediaId) {
      return buildUploadStatusEndpoint(mediaId);
    }

    function markAsQueued(mediaId) {
      return buildUploadStatusEndpoint(mediaId).post({status: "QUEUED"});
    }

    return {
        prepare,
        upload,
        loadUriImage
    };
}]);
