import { NgModule, ModuleWithProviders, SkipSelf, Optional } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { Configuration } from './configuration';

import { AuthenticationService } from './api/authentication.service';
import { DashboardService } from './api/dashboard.service';
import { DashboardWidgetService } from './api/dashboardWidget.service';
import { EventSourceService } from './api/eventSource.service';
import { SerializableValueService } from './api/serializableValue.service';
import { SystemService } from './api/system.service';
import { ViewService } from './api/view.service';

@NgModule({
  imports:      [ CommonModule, HttpClientModule ],
  declarations: [],
  exports:      [],
  providers: [
    AuthenticationService,
    DashboardService,
    DashboardWidgetService,
    EventSourceService,
    SerializableValueService,
    SystemService,
    ViewService ]
})
export class ApiModule {
    public static forRoot(configurationFactory: () => Configuration): ModuleWithProviders {
        return {
            ngModule: ApiModule,
            providers: [ { provide: Configuration, useFactory: configurationFactory } ]
        }
    }

    constructor( @Optional() @SkipSelf() parentModule: ApiModule) {
        if (parentModule) {
            throw new Error('ApiModule is already loaded. Import your base AppModule only.');
        }
    }
}
