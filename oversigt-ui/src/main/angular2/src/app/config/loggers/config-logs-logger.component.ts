import { Component, OnInit, Input } from '@angular/core';
import { LoggerInfo, SystemService } from 'src/oversigt-client';
import { NzMessageService } from 'ng-zorro-antd';

@Component({
  selector: 'app-config-logs-logger',
  templateUrl: './config-logs-logger.component.html',
  styleUrls: ['./config-logs-logger.component.css']
})
export class ConfigLogsLoggerComponent implements OnInit {
  possibleLogLevels: string[] = [];
  loggerInfos: LoggerInfo[] = [];

  constructor(
    private ss: SystemService,
    private message: NzMessageService
  ) { }

  ngOnInit() {
    this.ss.getLogLevels().subscribe(
      levels => {
        this.possibleLogLevels = levels;
      },
      error => {
        console.error(error);
        alert(error);
        // TODO
      }
    );
    this.ss.getLoggers(false).subscribe(
      loggers => {
        this.loggerInfos = loggers;
      },
      error => {
        console.error(error);
        alert(error);
        // TODO
      }
    );
  }

  setLogLevel(loggerName: string, level: string): void {
    this.ss.setLogLevel(loggerName, level).subscribe(
      ok => {
        this.loggerInfos.filter(info => info.name === loggerName)[0].level = level;
        // TODO compute effective level
        this.message.success('Logger "' + loggerName + '" has been set to level "' + level + '".');
      },
      error => {
        console.error(error);
        alert(error);
        // TODO
      }
    );
  }

}
