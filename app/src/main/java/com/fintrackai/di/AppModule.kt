package com.fintrackai.di

import android.content.Context
import androidx.room.Room
import com.fintrackai.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.fintrackai.data.local.db.AppDatabase
import com.fintrackai.data.local.db.MIGRATION_1_2
import com.fintrackai.data.local.db.MIGRATION_2_3
import com.fintrackai.data.local.db.MIGRATION_3_4
import com.fintrackai.data.local.db.MIGRATION_4_5
import com.fintrackai.data.local.db.MIGRATION_5_6
import com.fintrackai.data.local.db.MIGRATION_6_7
import com.fintrackai.data.local.db.MIGRATION_7_8
import com.fintrackai.data.local.db.MIGRATION_8_9
import com.fintrackai.data.local.db.MIGRATION_9_10
import com.fintrackai.data.local.db.MIGRATION_10_11
import com.fintrackai.data.local.db.MIGRATION_11_12
import com.fintrackai.data.local.db.MIGRATION_12_13
import com.fintrackai.data.local.db.MIGRATION_13_14
import com.fintrackai.data.local.db.MIGRATION_14_15
import com.fintrackai.data.local.db.MIGRATION_15_16
import com.fintrackai.data.local.db.MIGRATION_16_17
import com.fintrackai.data.local.db.MIGRATION_17_18
import com.fintrackai.data.local.db.MIGRATION_18_19
import com.fintrackai.data.local.db.MIGRATION_19_20
import com.fintrackai.data.local.db.MIGRATION_20_21
import com.fintrackai.data.local.db.MIGRATION_21_22
import com.fintrackai.data.local.db.RemoteMerchantCategoryDao
import com.fintrackai.data.local.db.SmsPatternDao
import com.fintrackai.data.local.db.AccountMappingDao
import com.fintrackai.data.local.db.BudgetDao
import com.fintrackai.data.local.db.CustomCategoryDao
import com.fintrackai.data.local.db.MerchantMappingDao
import com.fintrackai.data.local.db.MerchantNameMappingDao
import com.fintrackai.data.local.db.RecurringDao
import com.fintrackai.data.local.db.ReminderDao
import com.fintrackai.data.local.db.TransactionDao
import com.fintrackai.data.remote.ForexApiService
import com.fintrackai.data.remote.GroqApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "fintrackai.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideCustomCategoryDao(db: AppDatabase): CustomCategoryDao = db.customCategoryDao()
    @Provides fun provideMerchantMappingDao(db: AppDatabase): MerchantMappingDao = db.merchantMappingDao()
    @Provides fun provideMerchantNameMappingDao(db: AppDatabase): MerchantNameMappingDao = db.merchantNameMappingDao()
    @Provides fun provideAccountMappingDao(db: AppDatabase): AccountMappingDao = db.accountMappingDao()
    @Provides fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideRecurringDao(db: AppDatabase): RecurringDao = db.recurringDao()
    @Provides fun provideReminderDao(db: AppDatabase): ReminderDao = db.reminderDao()
    @Provides fun provideSmsPatternDao(db: AppDatabase): SmsPatternDao = db.smsPatternDao()
    @Provides fun provideRemoteMerchantCategoryDao(db: AppDatabase): RemoteMerchantCategoryDao = db.remoteMerchantCategoryDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideGroqApiService(client: OkHttpClient): GroqApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.groq.com/openai/v1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideForexApiService(client: OkHttpClient): ForexApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.frankfurter.app/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ForexApiService::class.java)
    }

    @Provides
    @Named("groqApiKey")
    fun provideGroqApiKey(): String = BuildConfig.GROQ_API_KEY

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(@ApplicationContext context: Context): FirebaseAnalytics =
        FirebaseAnalytics.getInstance(context)

    @Provides
    @Singleton
    fun provideFirebaseCrashlytics(): FirebaseCrashlytics = FirebaseCrashlytics.getInstance()
}
