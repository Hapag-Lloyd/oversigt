import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  constructor(

  ) { }

  info(message: string) {
    alert(message); // TODO: implement info message
  }

  error(message: string) {
    alert(message); // TODO: implement error message
  }

  success(message: string) {
    alert(message);
  }

  warning(message: string) {
    alert(message);
  }
}
