# iOS 18+ Limited Contacts Access

Starting with iOS 18, Apple introduced a new "Limited Access" tier for Contacts, similar to the Photos limited access introduced in iOS 14. Users can now choose to share only specific contacts with an app, rather than their entire address book.

`Grant` supports this natively via `GrantStatus.PARTIAL_GRANTED` when querying `AppGrant.CONTACTS` on iOS 18+.

## Using `CNContactPickerViewController`

If you do not need continuous access to the entire address book in the background, you should avoid requesting `AppGrant.CONTACTS` entirely. Instead, use `CNContactPickerViewController`.

This controller runs out-of-process and allows the user to pick contacts to return to your app, **without requiring any permission prompts**.

```swift
import ContactsUI

// In your iOS specific code (or via expect/actual)
class MyContactPickerDelegate: NSObject, CNContactPickerDelegate {
    func pickContact(from viewController: UIViewController) {
        let contactPicker = CNContactPickerViewController()
        contactPicker.delegate = self
        viewController.present(contactPicker, animated: true, completion: nil)
    }

    func contactPicker(_ picker: CNContactPickerViewController, didSelect contact: CNContact) {
        // You now have access to this specific contact!
        print("Selected: \(contact.givenName)")
    }
}
```

## Handling `PARTIAL_GRANTED` in Grant

If you still need traditional permission (e.g. you are building a dialer replacement or backup app) and use `AppGrant.CONTACTS`, you should handle the `PARTIAL_GRANTED` status:

```kotlin
val contactsGrant = GrantHandler(grantManager, AppGrant.CONTACTS, viewModelScope)

contactsGrant.request { status ->
    when (status) {
        GrantStatus.GRANTED -> {
            // Full access: sync all contacts
        }
        GrantStatus.PARTIAL_GRANTED -> {
            // Limited access (iOS 18+): You can only see what they selected.
            // You may prompt them to add more by routing to settings,
            // or accept the limited subset.
        }
        else -> { }
    }
}
```