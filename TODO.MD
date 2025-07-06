# Problems that need solving

1) ~~Incremental / Lazy artifact resolution : Artifacts should not be resolved if their corresponding archive is already cached.~~
2) ~~Removing the `jobs` API : I don't think there is any longer a good reason to use this.~~ Instead possibly implement a lightweight progress tracker.
3) `Changing` artifacts : During caching there should be an option to mark an artifact as changing 