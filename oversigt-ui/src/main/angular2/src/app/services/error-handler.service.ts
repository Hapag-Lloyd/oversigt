import { Injectable } from '@angular/core';
import { NotificationService } from './notification.service';
import { HttpErrorResponse } from '@angular/common/http';

@Injectable({
  providedIn: 'root'
})
export class ErrorHandlerService {

  constructor(
    private notification: NotificationService,
  ) { }

  createErrorHandler(reaction: string | (() => void), callback?: (() => void)): (error: any) => void {
    return (error: HttpErrorResponse) => {
      this.logError(error);
      console.log(error);
      const status: number = +error.status;
      switch (status) {
        case 0: // server not reachable
        default: // unknown error
          console.log('Looks like the server was not reachable: ', error.statusText);
          this.notification.warning('It looks like the Oversigt server is not reachable...');
          this.showErrorPage();
          return;
        case 401: // unauthorized
          alert('You tried to perform an action you are not authorized to.');
          // TODO: maybe we need to log in?
          break;
        case 403: // forbidden
          // TODO: was tun wir hier?
          break;
      }
      // TODO: if there are detail messages, show them somehow
      if (reaction) {
        if (typeof reaction === 'string') {
          this.notification.error(reaction.trim() + ' failed');
        } else {
          reaction();
        }
      }
      if (callback) {
        callback();
      }
    };
  }

  createZeroHandler(handler: (message: string) => void): (error: any) => void {
    return (error: HttpErrorResponse) => {
      this.logError(error);
      if (+error.status === 0) {
        // server not reachable
        console.log('Looks like the server was not reachable: ', error.statusText);
        this.notification.warning('It looks like the Oversigt server is not reachable...');
        this.showErrorPage();
      } else {
        handler(this.getMessage(error));
      }
    };
  }

  private logError(error: HttpErrorResponse): void {
    const message = this.getMessage(error);
    const details = this.getDetails(error);
    console.error('The request failed with code', error.status, 'with server message', message, ' - details:', ...details);
  }

  private getMessage(error: HttpErrorResponse): string {
    return (error.error.message) ? error.error.message : undefined;
  }

  private getDetails(error: HttpErrorResponse): string[] {
    return (error.error && error.error.errors) ? error.error.errors : [];
  }


  private showErrorPage(): void {
    // TODO: note current URL and show error page.
  }
}
