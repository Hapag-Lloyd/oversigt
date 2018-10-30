import { Injectable } from '@angular/core';
import { Subject, Observable } from 'rxjs';
import { EventSourceInstanceInfo } from 'src/oversigt-client';

@Injectable()
export class EventsourceSelectionService {
  private selectedEventSourceSource: Subject<string>;

  private _selectedEventSource = this.selectedEventSourceSource.asObservable();

  get selectedEventSource(): Observable<string> {
    return this._selectedEventSource;
  }

  selectEventSource(info: string): void {
    this.selectedEventSourceSource.next(info);
  }
}
