
### 普通多模块项目（非组件化、插件化项目）

我们在[Dagger2多模块项目依赖方式选择一]()的基础上改造实现

dagger.android的核心思想是在每个Component收集两个Map

```kotlin
Map<Class<?>, Provider<AndroidInjector.Factory<?>>> injectorFactoriesWithClassKeys,
Map<String, Provider<AndroidInjector.Factory<?>>> injectorFactoriesWithStringKeys
```

这两个Map定义在AndroidInjectionModule中

```java
@Beta
@Module
public abstract class AndroidInjectionModule {
  @Multibinds
  abstract Map<Class<?>, AndroidInjector.Factory<?>> classKeyedInjectorFactories();

  @Multibinds
  abstract Map<String, AndroidInjector.Factory<?>> stringKeyedInjectorFactories();

  private AndroidInjectionModule() {}
}
```

dagger.android会把把收集到的这两个Map注入到DispatchingAndroidInjector中，dagger.android就是通过这个DispatchingAndroidInjector注入到Activity,Fragment中

怎么收集呢

首先定义一个 xxxBindModule ，将要注入的Activity,fragment用@ContributesAndroidInjector注解

dagger.android会把这些收集到前面的Map中去

```kotlin
@Module(includes = [AndroidInjectionModule::class])
abstract class NewsBindModule {
    @ContributesAndroidInjector
    abstract fun newsActivity(): NewsActivity
}
```

然后相应的Component的modules加上xxxBindModule,

去掉inject(XXXActivity)这样的一大堆声明方法,干净多了

```kotlin
@NewsScope
@Subcomponent(modules = [NewsModule::class, NewsBindModule::class])
interface NewsComponent {

    @Subcomponent.Factory
    interface Factory {
        fun create(): NewsComponent
    }
}
```

之后按照dagger.android用法要让Application实现HasAndroidInjector接口，并注入dispatchingAndroidInjector实例

```kotlin
class AppApplication : BaseApplication(), NewsComponentProvider, UserComponentProvider, HasAndroidInjector {
  	
  	@Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>
  	
    lateinit var appComponent: AppComponent
    override fun onCreate() {
        super.onCreate()
        appComponent = DaggerAppComponent.factory().create(this)
    }

    override fun provideNewsComponent(): NewsComponent {
        return appComponent.newsComponentFactory().create()
    }

    override fun provideUserComponent(): UserComponent {
        return appComponent.userComponentFactory().create()
    }
  
    override fun androidInjector(): AndroidInjector<Any> {
        return dispatchingAndroidInjector
    }
}
```

再在Component加上一个注入到上面Appliction的方法(因为news模块拿不到AppApplication的引用，直接注入到Any好了)

```kotlin
@NewsScope
@Subcomponent(modules = [NewsModule::class, NewsBindModule::class])
interface NewsComponent {

    @Subcomponent.Factory
    interface Factory {
        fun create(): NewsComponent
    }
  
    fun inject(any: Any)
}
```

然后在AppApplication中注入

```kotlin
class AppApplication : BaseApplication(), NewsComponentProvider, UserComponentProvider {
  	
  	@Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>
  	
    lateinit var appComponent: AppComponent
    override fun onCreate() {
        super.onCreate()
        appComponent = DaggerAppComponent.factory().create(this)
      	NewsComponentHolder.newsComponent.inject(this)
    }

    override fun provideNewsComponent(): NewsComponent {
        return appComponent.newsComponentFactory().create()
    }

    override fun provideUserComponent(): UserComponent {
        return appComponent.userComponentFactory().create()
    }
  
    override fun androidInjector(): AndroidInjector<Any> {
        return dispatchingAndroidInjector
    }
}
```

最后在Activity，fragment的onCreate方法中加入AndroidInjection.inject(this),注意要放在super.onCreate(savedInstanceState)前面,我们把这一步放在BaseActivity,BaseFragment里

```kotlin
open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
    }
}
```

然后Activity只要继承BaseActivity就可以了，不需要写任何注入代码了，像平时使用一样了，想要注入对象的变量加 @Inject就可以了

```kotlin
class NewsActivity : BaseActivity() {

    @Inject
    lateinit var set: Set<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news)
        text.text = set.toString()
    }
}
```

这种写法对于单模块项目没有问题，但是对多模块项目来说这有问题了，上面我们只注入了news模块的，user模块的没有。我们有多个Component,但是这里只有一个dispatchingAndroidInjector，你用哪个Component注入都不全，后面注入的会覆盖前面注入的。所以这里要改造下

从前面我们知道一个Component最终生成一个DispatchingAndroidInjector，多个Component我们把它们都收集起来

我们先定义一个BaseDispatchingInjector,它相当于前面的AppApplication,接收一个Component注入的DispatchingAndroidInjector

```kotlin
class BaseDispatchingInjector  {
    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>
}
```

然后把每个Component里的inject(any: Any)改成inject(baseDispatchingInjector: BaseDispatchingInjector)

```kotlin
@NewsScope
@Subcomponent(modules = [NewsModule::class, NewsBindModule::class])
interface NewsComponent {

    @Subcomponent.Factory
    interface Factory {
        fun create(): NewsComponent
    }

    fun inject(baseDispatchingInjector: BaseDispatchingInjector)
}
@UserScope
@Subcomponent(modules = [UserModule::class, UserBindModule::class])
interface UserComponent {
    @Subcomponent.Factory
    interface Factory {
        fun create(): UserComponent
    }

    fun inject(baseDispatchingInjector: BaseDispatchingInjector)
}
```

这样注入

```kotlin
val userDispatchingInjector = BaseDispatchingInjector()
UserComponentHolder.userComponent.inject(userDispatchingInjector)
val newsDispatchingInjector = BaseDispatchingInjector()
NewsComponentHolder.newsComponent.inject(newsDispatchingInjector)
```

这样我们每个模块都得到一个BaseDispatchingInjector,并且里面每个Activity,Fragment对应的Map都注入好了

然后就要定义一个MultiModuleAndroidInjector把每个模块的BaseDispatchingInjector整合到一起成为一个单独的AndroidInjector

```kotlin
class MultiModuleAndroidInjector : AndroidInjector<Any> {

    private val injectors = mutableListOf<BaseDispatchingInjector>()

    fun addInjector(injector: HasDispatchingInjector) {
        injectors.add(injector)
    }

    override fun inject(instance: Any) {
        val wasInjected = injectors.any { it.dispatchingAndroidInjector.maybeInject(instance) }
        if (!wasInjected) {
            throw IllegalArgumentException("injection failed")
        }
    }
}
```

这个MultiModuleAndroidInjector在注入的时候会每个BaseDispatchingInjector都去尝试看能不能注入，这样就把所有Component的注解都遍历了

看AppApplication最后实现

```kotlin
class AppApplication : BaseApplication(), NewsComponentProvider, UserComponentProvider,
    HasAndroidInjector {

    lateinit var appComponent: AppComponent

    private val multiModuleAndroidInjector = MultiModuleAndroidInjector()

    override fun onCreate() {
        super.onCreate()
        appComponent = DaggerAppComponent.factory().create(this)
        val userDispatchingInjector = BaseDispatchingInjector()
        UserComponentHolder.userComponent.inject(userDispatchingInjector)
        multiModuleAndroidInjector.addInjector(userDispatchingInjector)
        val newsDispatchingInjector = BaseDispatchingInjector()
        NewsComponentHolder.newsComponent.inject(newsDispatchingInjector)
        multiModuleAndroidInjector.addInjector(newsDispatchingInjector)
    }

    override fun provideNewsComponent(): NewsComponent {
        return appComponent.newsComponentFactory().create()
    }

    override fun provideUserComponent(): UserComponent {
        return appComponent.userComponentFactory().create()
    }

    override fun androidInjector(): AndroidInjector<Any> {
        return multiModuleAndroidInjector
    }
}
```