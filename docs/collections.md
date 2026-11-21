# Reading collections

Collections organize document references; they never copy document contents or delete provider files. The implementation budget is 20 named collections, 30 references per collection and 200 memberships in total. Names are trimmed, limited to 80 characters and unique ignoring case. A URI occurs at most once within a collection. Different collections can refer to the same document.

Stored metadata consists of a random collection ID, its name, member document URI and display filename (256 characters maximum). It uses private app preferences, is excluded from backup with all app data, and has no automatic expiry. Collections independently own existing persisted read grants alongside recents, bookmarks and open documents. Removing a membership releases a grant only when no other owner still needs it.

Removing or clearing recent history leaves collections intact. Delete a collection or remove its members to forget those references; neither action deletes a file. Opening a member uses the ordinary five-document session limit and access/recovery UI. Revoked, moved and unavailable references are not silently replaced. Reselecting asks for an explicit replacement through the system picker and confirms the named reference being changed. Collection contents may be temporarily unavailable when their provider offers only a transient grant.

Use **Collections** on home or in the reader menu to create, rename and delete a collection, add existing recent references, open a member, remove a member or reselect it. Native labeled controls support keyboard focus and enlarged text. Human spoken TalkBack and physical-provider acceptance remain part of #56.
