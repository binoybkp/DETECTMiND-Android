package com.research.detectmind.di

import com.research.detectmind.service.collectors.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds @IntoSet @Singleton
    abstract fun bindBatteryCollector(impl: BatteryCollector): SensorCollector

    @Binds @IntoSet @Singleton
    abstract fun bindScreenStateCollector(impl: ScreenStateCollector): SensorCollector

    @Binds @IntoSet @Singleton
    abstract fun bindLightCollector(impl: LightCollector): SensorCollector

    @Binds @IntoSet @Singleton
    abstract fun bindAppUsageCollector(impl: AppUsageCollector): SensorCollector

    @Binds @IntoSet @Singleton
    abstract fun bindLocationCollector(impl: LocationCollector): SensorCollector

    @Binds @IntoSet @Singleton
    abstract fun bindNotificationSensorCollector(impl: NotificationSensorCollector): SensorCollector

    @Binds @IntoSet @Singleton
    abstract fun bindCallsCollector(impl: CallsCollector): SensorCollector

    @Binds @IntoSet @Singleton
    abstract fun bindSmsCollector(impl: SmsCollector): SensorCollector
}
