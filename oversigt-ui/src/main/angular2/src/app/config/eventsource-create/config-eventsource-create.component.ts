import { Component, OnInit } from '@angular/core';
import { EventSourceService, EventSourceInfo } from 'src/oversigt-client';
import { Router } from '@angular/router';
import { NzMessageService } from 'ng-zorro-antd';

@Component({
  selector: 'app-config-eventsource-create',
  templateUrl: './config-eventsource-create.component.html',
  styleUrls: ['./config-eventsource-create.component.css']
})
export class ConfigEventsourceCreateComponent implements OnInit {
  eventSourceInfos: EventSourceInfo[];

  searchText = '';
  filteredEventSourceInfos: EventSourceInfo[];

  constructor(
    private router: Router,
    private ess: EventSourceService,
    private messages: NzMessageService,
  ) { }

  ngOnInit() {
    this.ess.listAvailableEventSources().subscribe(
      list => {
        this.eventSourceInfos = list.sort((a, b) => a.name.toLowerCase() > b.name.toLowerCase() ? 1 : -1);
        this.filter();
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
  }

  filter(): void {
    const lowerCaseSearchText = this.searchText.toLowerCase();
    this.filteredEventSourceInfos = this.eventSourceInfos.filter(esi => {
      return lowerCaseSearchText === ''
        || esi.name.toLowerCase().indexOf(lowerCaseSearchText) >= 0
        || esi.description.toLowerCase().indexOf(lowerCaseSearchText) >= 0;
    });
  }

  createEventSource(key: string) {
    let timeDone = false;
    let createdId: string = null;
    const id = this.messages.loading('Creating event source..', { nzDuration: 0 }).messageId;
    const check = () => {
      if (timeDone && createdId !== null) {
        this.messages.remove(id);
        this.router.navigateByUrl('config/eventSources/' + createdId);
      }
    }
    setTimeout(_ => {
      timeDone = true;
      check();
    }, 1000);
    this.ess.createInstance(key).subscribe(
      ok => {
        createdId = ok.id;
        check();
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
  }

  showEventSourceInfo(key: string) {
    this.messages.warning('Not yet implemented.');
  }

}
