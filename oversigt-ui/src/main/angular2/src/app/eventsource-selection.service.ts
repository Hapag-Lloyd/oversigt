import { Injectable } from '@angular/core';
import { Subject, Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class EventsourceSelectionService {
  private _selectedEventSourceSubject = new Subject<string>();
  private _selectedEventSourceObservable = this._selectedEventSourceSubject .asObservable();

  constructor() { }

  get selectedEventSource(): Observable<string> {
    return this._selectedEventSourceObservable;
  }

  selectEventSource(eventSourceId: string): void {
    this._selectedEventSourceSubject.next(eventSourceId);
  }
}
