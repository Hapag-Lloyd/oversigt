import { Component, OnInit } from '@angular/core';
import { SystemService } from 'src/oversigt-client';

@Component({
  selector: 'app-config-server-configuration',
  templateUrl: './config-server-configuration.component.html',
  styleUrls: ['./config-server-configuration.component.css']
})
export class ConfigServerConfigurationComponent implements OnInit {
  config: any = null;

  constructor(
    private systemService: SystemService,
  ) { }

  ngOnInit() {
    this.systemService.readConfiguration().subscribe(
      config => {
        this.config = config;
      }
    );
  }

}
