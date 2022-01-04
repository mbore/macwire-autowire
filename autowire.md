#1. Intro

#2. What is macwire?
If you don’t know yet what MacWire is: a lightweight and non-intrusive Scala dependency injection library, and in many cases a replacement for DI containers.
In case you're interested in DI in Scala, I highly recommend you [this](https://di-in-scala.github.io) guide.

#3. The Goal of autowire
   1. `wire`'s limitations
MacWire 2.0 exposes quite a few decent features and modules, but the general idea underlying this library may be depicted using the `wire[A]` feature.
Let's consider the first example from the documentation:
```Scala
class DatabaseAccess()
class SecurityFilter()
class UserFinder(databaseAccess: DatabaseAccess, securityFilter: SecurityFilter)
class UserStatusReader(userFinder: UserFinder)

trait UserModule {
    import com.softwaremill.macwire._

    lazy val theDatabaseAccess   = wire[DatabaseAccess]
    lazy val theSecurityFilter   = wire[SecurityFilter]
    lazy val theUserFinder       = wire[UserFinder]
    lazy val theUserStatusReader = wire[UserStatusReader]
}
```
Details on how wiring works you can find [here](https://github.com/softwaremill/macwire#how-wiring-works), but for the sake of this article 
lets reduce the procedure to three most important steps:
1. search of available instances in the given scope
2. select an appropriate factory method (appropriate in this case means, a method which may be used with the available instances)
3. generate the code

So let's work through the given example. In the given scope we've got 4 available instances, but for each of them we need to create an instance.
It means, that the result of the first step for each `wire[A]` application is exactly the same, namely `Set(theDatabaseAccess, theSecurityFilter, theUserFinder, theUserStatusReader)`.
For both `theDatabaseAccess` and `theSecurityFilter` remaining steps are exactly the same.
2. only available factory methods are empty primary constructors in both cases, so it selects them
3. generated code is a simple application of these methods
```Scala
    lazy val theDatabaseAccess   = new DatabaseAccess()
    lazy val theSecurityFilter   = new SecurityFilter()
```

The other applications of `wire[A]` are not such straightforward. Let's walk through the remaining steps for `theUserFinder`
2. the only available factory method is the primary constructor, which takes two arguments `DatabaseAccess` and `SecurityFilter`. 
We need to check now in the result of the first step if the required instances are available in the given scope. Thankfully, they are.
We can use `theDatabaseAccess` and `theSecurityFilter`.
3. the generated code in this case is also just an application of the resolved instances
```Scala
    lazy val theUserFinder       = new UserFinder(theDatabaseAccess, theSecurityFilter)
```
For the last `wire[A]` application the process is quite similar to the previous one, so lets jump to the result code
```Scala
    lazy val theUserStatusReader = new UserStatusReader(theUserFinder)
```

And that's it, we've got all dependencies injected into `UserStatusReader` at compile time.


But what if in this piece of code we're actually only interested in the `theUserStatusReader`, because we want to use it in some other place and all the remaining 
instances we created only to get that one? The goal of `autowire` is to get rid of these useless definitions and make MacWire much more useful in the Scala FP environment.   

#4. First attempt - wireRec
   1. overview
   2. inspiration (https://github.com/yakivy/jam)
   3. weaknesses

The first attempt to deal with useless definitions has emerged as a side effect of migrating MacWire 2.0 to Scala 3 and was inspired by 
a small library called [Jam](https://github.com/yakivy/jam). It may be called recursive wiring and is currently exposed in the API as `wireRec[A]`. The difference between
`wire[A]` and `wireRec[A]` may be observed in the second step of the described process. When `wire[A]` is not able to find any factory method which 
may be used with the instances available in the scopt it fails. `wireRec[A]`, on the other hand, in such case is trying to create missing instances 
with the instances available in the scope. It may sound a little mysterious, so let me walk through the slightly changed previous example
```Scala
class DatabaseAccess()
class SecurityFilter()
class UserFinder(databaseAccess: DatabaseAccess, securityFilter: SecurityFilter)
class UserStatusReader(userFinder: UserFinder)

trait UserModule {
    import com.softwaremill.macwire._

    lazy val theUserStatusReader = wireRec[UserStatusReader]
}
```
At first glance, it looks way better, but let's check under tho hood how does it work.
1. We've got only one instance in the scope, which unfortunately will not help us in the process. The result of this step is `Set(theUserStatusReader)`
2. The only available factory method is the primary constructor, which take one argument. We are not able to use it with the available instances
   (which means that we cannot jump to the next step), so we need to try to create the required instances in this step. To do so, we can 
replace the existing code with
```Scala
    lazy val theUserStatusReader = new UserStatusReader(wireRec[UserFinder])
```
Now we are running the same procedure for the nested `wireRec[UserFinder]`. Again, we're not able to directly create a new instance of `UserFinder` with
the available instances, so we try to create missing instances. The result code at this stage is
```Scala
    lazy val theUserStatusReader = new UserStatusReader(new UserFinder(wireRec[DatabaseAccess], wireRec[SecurityFilter]))
```
Now we're finally able to wire the required instances, just like in the previous example, than we cane generate the following code
```Scala
    lazy val theUserStatusReader = new UserStatusReader(new UserFinder(new DatabaseAccess(), newSecurityFilter()))
```

This approach is quite powerful, since it creates intermediate instances based on all instances from the given scope and also was
straightforward to implement. But on the other hand it brings few new potential problems to the table like:
* it does not support re-using intermediate instances
* unintended absence of some instances may cause creation of other instances
* debugging of the process is definitely not-trivial task

To address these and others issues, and also add integration with cats-effect library we decided to work on new approach.   

#5. Autowire
   1. Overview
   2. State of work
   3. Road map
   4. Example
   5. Open points

The most important difference between `wireRec[A]` and `autowire[A]` is that the latter requires a list of instances which may be used in the process.
The goal of this change is to make it easier to reason about set of available instances. It also made it easier introduce re-usability of instances.
`autowire[A]` brings also integration with cats-effect library, therefore it always returns instance wrapped in `cats.effect.Resource`. The integration
means, that user may pass `Resource[IO, X]` or `IO[X]` and the wrapped instances of `X` will be used in the process of creation of `A`. Beside that
`autowire[A]` takes also factory methods as arguments and use them in the same way as `wireWih[A]`. To get better understanding of the difference
let me compare the new process with that already well know from `wire[A]`.
Step 1:
   Based on the dependencies list we define a set of usable instances in the following way: 
   `Resource[IO, X]` -> we may use instance of `X`
   `IO[X]` -> we may use instance of `X`
   `Y => X` -> we may use instance of `X` if we are able to provide an instance of `Y`
Step 2:
   This step is quite similar to the one known from `wireRec[A]`. We chose the factory method (from input factory methods, primary constructor and apply methods defined in 
   the companion object), try to create an instance and in case of failure we run `autowire[A]` for the missing instances with the same input dependencies.
Step 3:
   It's code generation step. Firstly we convert `IO[A]` into `Resource[IO, A]`, then we compose the list of resources in the input order, and finally we use
   the available instances in the tree created in the previous step.

Let's see how it works with our example:
```Scala
class DatabaseAccess()
class SecurityFilter()
class UserFinder(databaseAccess: DatabaseAccess, securityFilter: SecurityFilter)
class UserStatusReader(userFinder: UserFinder)

trait UserModule {
   import com.softwaremill.macwire._

   lazy val theDatabaseAccess: Resource[IO, DatabaseAccess] = ???
   lazy val theSecurityFilter: Resource[IO, SecurityFilter] = ???
   
   lazy val theUserStatusReader: Resource[IO, UserStatusReader] = autowire[IO, UserStatusReader](theDatabaseAccess, theSecurityFilter)
}
```
1. The dependencies list contains two elements, so we will be able to use `DatabaseAccess` (name: `da`) and `SecurityFilter` (name: `sf`) to create result instance of `UserStatusReader`
2. The only available factory method is the primary constructor, so we choose it. Unfortunately we're not able to use it at this point, because 
   we don't have an instance of `UserFinder` available, so let's try to create one. Again, we select the primary constructor, but in this case 
   we're able to use it with instances from the dependencies list. The result tree will look like `new UserStatusReader(new UserFinder(da, sf))`
3. The last step is to generate the code. Firstly we compose the resource in the input order `theDatabaseAccess.flatMap(da => theSecurityFilter.flatMap(sf => ???))`
   and then we use the known instances in the result tree. 
```Scala
class DatabaseAccess()
class SecurityFilter()
class UserFinder(databaseAccess: DatabaseAccess, securityFilter: SecurityFilter)
class UserStatusReader(userFinder: UserFinder)

trait UserModule {
   import com.softwaremill.macwire._

   lazy val theDatabaseAccess: Resource[IO, DatabaseAccess] = ???
   lazy val theSecurityFilter: Resource[IO, SecurityFilter] = ???
   
   lazy val theUserStatusReader: Resource[IO, UserStatusReader] = theDatabaseAccess.flatMap(da => theSecurityFilter.flatMap(sf => Resource.pure[IO](new UserStatusReader(new UserFinder(da, sf)))))
}
```

`autowire[A]` is a great step forward to make macwire useful in the Scala fp word, but it may be also useful in the future beyond that. 
Currently, we have few open points on which the discussion is needed. The most important one is described in [this](https://github.com/softwaremill/macwire/issues/184) 
issue, and it basically is about which instances may be created by macwire. 
We also had some conversations about creating resources in [here](https://github.com/softwaremill/macwire/issues/173).
Another interesting point may be parallel initialization of resources also mentioned [here](https://github.com/softwaremill/macwire/issues/173).
There are some minor improvements that should be done soon like support for factory methods which return resources, prevent [ambiguous instances](https://github.com/softwaremill/macwire/issues/181)
and [fail on unused dependencies](https://github.com/softwaremill/macwire/issues/182).


At this point it extremely important for us to hear what solutions for the mentioned problems and new features are expected by the users.
So where should we go next with this feature? Let us know in the short survey and do not hesitate to create & comment the issues.



# 6. Summary
   1. survey