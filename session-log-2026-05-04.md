# Session Log — 2026-05-04

## Task
Investigate two UI bugs in the kahuna project introduced by commit `1322691719fe180a6004398880bf265007c3c3cf` ("Sort by Taken Date UI Changes").

---

## Bug 1 — "Back to search" loses "Free to use only" state

### Symptom
With "Free to use only" checked, clicking into an image then clicking "Back to search" caused the checkbox to lose its checked state.

### Root Cause — `kahuna/public/js/search/query.js`

New code added in `watchSearchChange`:

```js
// BUGGY (new code):
let showPaid = newFilter.nonFree ? newFilter.nonFree : false;
if (sender && sender == "filterChange" && !newFilter.nonFree) {
  showPaid = ctrl.user.permissions.showPaid;  // e.g. true
}
storage.setJs("isNonFree", showPaid, true);
```

When the `$watchCollection` on `ctrl.filter` fires (sender="filterChange") during controller
initialisation and `nonFree` is `undefined` (because "Free to use only" is checked), the new
branch incorrectly stores `user.permissions.showPaid` (potentially `true`) to `isNonFree`
session storage.

When navigating to an image preview, the `search.results` controller is destroyed. On "Back to
search" the controller is re-instantiated. `getSession().then(...)` runs, reads `isNonFree=true`
from storage, sets `ctrl.filter.nonFree="true"`, and navigates with `nonFree=true` — unchecking
the "Free to use only" box.

### Fix
Reverted `showPaid` computation to pre-commit behaviour (three-line diff):

```js
// FIXED:
const showPaid = newFilter.nonFree ? newFilter.nonFree : false;
storage.setJs("isNonFree", showPaid, true);
```

---

## Bug 2 — Logo click: "Free to use only" stays checked and next checkbox click broken

### Symptom
With "Free to use only" checked, clicking the Grid logo left the checkbox checked (should reset)
and the next click on any checkbox was broken.

### Root Cause — `gr-sort-control.tsx` and `gr-extended-sort-control.tsx`

The OLD `gr-sort-control.tsx` `handleLogoClick` only reset the visual selection:
```js
const handleLogoClick = (e: any) => {
  setSelection(defSort);  // visual reset only — no navigation
};
```

The NEW version added a call to `props.onSortSelect`:
```js
// BUGGY (new code):
const handleLogoClick = (e: any) => {
  setSortOption(DefaultSortOption);
  props.onSortSelect(DefaultSortOption);  // triggers updateSortChips -> $state.go
};
```

And the entirely new `gr-extended-sort-control.tsx` did the same:
```js
// BUGGY (new code):
const handleLogoClick = (e: any) => {
  setSortOption(DefaultSortOption);
  setUserTakenSelect(false);
  props.onSortSelect(DefaultSortOption, 'with', false);  // triggers updateSortChange -> $state.go
};
```

`window.dispatchEvent` is synchronous. Both `handleLogoClick` handlers fire inside the same call
stack as `onLogoClick` in `index.js`. Each `props.onSortSelect` call triggers
`updateSortChips`/`updateSortChange`, which each call `$state.go({...ctrl.filter, orderBy:'newest'})` 
and `$state.go({...$stateParams, orderBy:'newest'})` respectively — with the OLD `nonFree`
value (`undefined`) still in `ctrl.filter`/`$stateParams`.

In AngularJS UI-Router, the last `$state.go` in a synchronous chain wins. The sort controls'
`$state.go` calls override the logo-click's intended `$state.go({nonFree: showPaid})` from
`index.js`. Result: `nonFree` stays `undefined`, "Free to use only" remains checked.

The competing navigations also leave React components and Angular scope in an inconsistent state
(React state vs. Angular model out of sync, potentially duplicated event listeners), which caused
subsequent checkbox interactions to be broken.

### Fix
Removed `props.onSortSelect` from both `handleLogoClick` handlers:

**`gr-sort-control.tsx`:**
```js
const handleLogoClick = (e: any) => {
  setSortOption(DefaultSortOption);
  // navigation is handled by index.js onLogoClick
};
```

**`gr-extended-sort-control.tsx`:**
```js
const handleLogoClick = (e: any) => {
  setSortOption(DefaultSortOption);
  setUserTakenSelect(false);
  // navigation is handled by index.js onLogoClick
};
```

---

## Files Changed

| File | Change |
|------|--------|
| `kahuna/public/js/search/query.js` | Removed buggy `showPaid = ctrl.user.permissions.showPaid` branch in `watchSearchChange` |
| `kahuna/public/js/components/gr-sort-control/gr-sort-control.tsx` | Removed `props.onSortSelect(DefaultSortOption)` from `handleLogoClick` |
| `kahuna/public/js/components/gr-sort-control/gr-extended-sort-control.tsx` | Removed `props.onSortSelect(DefaultSortOption, 'with', false)` from `handleLogoClick` |
