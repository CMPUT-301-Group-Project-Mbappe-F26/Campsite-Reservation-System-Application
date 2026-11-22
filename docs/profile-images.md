# Profile images (US 01.08.02 / issue #84)

The Profile screen supports choosing a photo, previewing it, saving it, replacing it,
and removing it. Cancel keeps the saved image. Removing a photo restores the initials
avatar. Personal info uses the same saved image. Existing accounts need no migration.

## Shared data contract for the admin image browser (US 03.06.01)

The admin image browser is not present in this branch. Its implementation can discover
the current profile photos by reading these fields on `users/{userId}`:

| Field | Value |
| --- | --- |
| `profileImageUrl` | Firebase Storage download URL; blank means no saved photo |
| `profileImagePath` | `profile-images/{userId}/{imageId}.jpg`; blank means no saved photo |

The storage object has `contentType: image/jpeg` and custom metadata `ownerId: userId`
and `imageType: profile`. Each replacement uses a new image ID. The browser should use
the user document as the source of current images, rather than showing every object
left in Storage. A failed cleanup can leave an unused object.

Selected sources are limited to 20 MB, decoded at no more than 1024 pixels per side,
oriented correctly, and encoded as JPEG before uploading (maximum 5 MB). The original
file's EXIF location metadata is not copied. Animated images use their first frame.

Uploads finish before the user document is changed. Only the two image fields change;
names, roles, and notification settings are preserved. Concurrent image changes are
checked in a Firestore transaction. Personal-info saves preserve the current image
even when their draft is older.
Previous files are cleaned up after saving, and
only after a server read confirms they are no longer the current image. Cleanup failure
does not undo a successful save. Profile deletion also attempts image cleanup while
the user is still authenticated.

## Firebase rules integration

Before testing uploads, open the project's Firebase Console > Storage and create its
default bucket if it has not been provisioned. Confirm the bucket shown there matches
`project_info.storage_bucket` in `app/google-services.json`; download the updated Android
configuration if it differs. An upload returning HTTP 404 with "The specified bucket
does not exist" is a bucket setup problem, not a missing local image or a rules denial.
If setup prompts for a billing plan or payment details, the project owner must decide
whether to enable it. This code change does not create a bucket or change billing.

No rules have been deployed by this change. Merge `profile-images.storage.rules` into
the existing Storage rules rather than replacing campsite-photo rules. The admin read
check uses the existing Firestore `users/{uid}.isAdmin` flag. Cross-service rule access
may require enabling the Firebase service permission when the rules are deployed.

Existing Firestore rules for `users/{userId}` must permit the signed-in user to update
`profileImageUrl` and `profileImagePath`. If those rules use an allowed-field list, add
these two fields. Keep existing restrictions on role changes. Admins need permission
to list/read user profiles for their image browser. An additional update rule, if the
existing one does not allow image fields, can be scoped as follows inside that match:

```text
allow update: if request.auth != null && request.auth.uid == userId
  && request.resource.data.diff(resource.data).affectedKeys()
    .hasOnly(['profileImageUrl', 'profileImagePath'])
  && request.resource.data.profileImageUrl is string
  && request.resource.data.profileImagePath is string
  && ((request.resource.data.profileImageUrl == '' && request.resource.data.profileImagePath == '')
    || (request.resource.data.profileImageUrl.matches('https://.*')
      && request.resource.data.profileImagePath.matches('profile-images/' + userId + '/[^/]+[.]jpg')));
```

These local changes do not create an admin image browser or deploy Firebase changes.
Verify add, relaunch, replace, cancel, remove, and failed-save retry on a test account
after the team has integrated the rules. Confirm the admin browser uses this contract
before marking its integration criterion complete.
