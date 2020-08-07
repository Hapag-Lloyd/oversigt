import { Component, OnInit } from '@angular/core';
import { SystemService } from 'src/oversigt-client';
import { ErrorHandlerService } from 'src/app/services/error-handler.service';

@Component({
  selector: 'app-config-server-configuration',
  templateUrl: './config-server-configuration.component.html',
  styleUrls: ['./config-server-configuration.component.css']
})
export class ConfigServerConfigurationComponent implements OnInit {
  config: any = null;

  constructor(
    private systemService: SystemService,
    private errorHandler: ErrorHandlerService,
  ) { }

  ngOnInit() {
    this.systemService.readConfiguration().subscribe(
      config => {
        this.config = config;
      },
      this.errorHandler.createErrorHandler('Reading configuration data')
    );
  }

}
