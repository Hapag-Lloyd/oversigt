import { Component, OnInit, Input } from '@angular/core';
import { SystemService } from 'src/oversigt-client';
import { ErrorHandlerService } from 'src/app/services/error-handler.service';

@Component({
  selector: 'app-config-logs-logfile',
  templateUrl: './config-logs-logfile.component.html',
  styleUrls: ['./config-logs-logfile.component.css']
})
export class ConfigLogsLogfileComponent implements OnInit {
  logfiles: string[] = [];
  content: string = null;
  selectedValue: string = null;

  constructor(
    private ss: SystemService,
    private errorHandler: ErrorHandlerService,
  ) { }

  ngOnInit() {
    this.ss.listLogFiles().subscribe(
      list => {
        this.logfiles = list.sort((a, b) => a.toLowerCase() < b.toLowerCase() ? 1 : -1);
      },
      this.errorHandler.createErrorHandler('Listing log files')
    );
  }

  loadLogFileContent(filename: string): void {
    // TODO: adjust URL so the log file name is part of the url
    this.selectedValue = filename;
    this.ss.getLogFileContent(filename, -100).subscribe(
      logLines => {
        this.content = logLines.reverse().join('\n');
      }
    );
  }

  downloadLogfile(filename: string): void {
    this.ss.getLogFileContent(filename).subscribe(
      logLines => {
        const content = logLines.join('\n');
        const blob = new Blob([content], { type: 'text/plain' });
        const url = window.URL.createObjectURL(blob);
        window.open(url);
      }
    );
  }

}
