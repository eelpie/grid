// `ratioString` is sent to the server, being `undefined` for `freeform` is expected 🙈
export const landscapeOld = {key: 'landscape OLD', ratio: 5 / 3, ratioString: '5:3', isHidden: true, displayName: "Landscape"};
export const landscape = {key: 'landscape', ratio: 5 / 4, ratioString: '5:4', displayName: 'Landscape'};
export const portrait = {key: 'portrait', ratio: 4 / 5, ratioString: '4:5', displayName: 'Portrait'};
export const video = {key: 'video', ratio: 16 / 9, ratioString: '16:9', displayName: 'Video'};
export const square = {key: 'square', ratio: 1, ratioString: '1:1', displayName: 'Square'};
export const freeform = {key: 'freeform', ratio: null, isDefault: true, displayName: 'Freeform'};

export const cropOptions = [landscapeOld, landscape, portrait, video, square, freeform];
