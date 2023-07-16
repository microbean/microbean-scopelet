# Destruction Sequence

The `NoneScopelet`:
* Uses the supplied `Factory` to create an instance (`I`)
* Registers the `Factory`'s `destroy` method with the `Destruction` (in an `Instance`)

So let's say there's a `None`-scoped `Host` that has a `None`-scoped `Parasite`.

Let's say the `Destruction` for `Host` is `DH`. Let's say the `Destruction` for `Parasite` is `DP`.

`DH` will contain the destroyer for `Host`. It will not contain any reference to `Parasite` or `DP`.

`DP` will contain the destroyer for `Parasite`. It will not contain any reference to `Host`.

If someone calls `DH.close()`:
* `DH` will be destroyed
* `DP` will not be destroyed
